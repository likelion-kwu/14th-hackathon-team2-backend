package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalysis;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzerException;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationInput;
import com.likelion.hackathon_be.routine.verification.application.StoredVerificationPhoto;
import com.likelion.hackathon_be.routine.verification.application.VerificationPhotoStorage;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultRoutineVerificationService implements RoutineVerificationService {

    private static final Set<String> SUPPORTED_PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final PhotoMissionTemplateRepository photoMissionTemplateRepository;
    private final PhotoVerificationAnalyzer photoVerificationAnalyzer;
    private final VerificationPhotoStorage verificationPhotoStorage;
    private final RoutineCompletionService routineCompletionService;

    public DefaultRoutineVerificationService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            PhotoMissionTemplateRepository photoMissionTemplateRepository,
            PhotoVerificationAnalyzer photoVerificationAnalyzer,
            VerificationPhotoStorage verificationPhotoStorage,
            RoutineCompletionService routineCompletionService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.photoMissionTemplateRepository = photoMissionTemplateRepository;
        this.photoVerificationAnalyzer = photoVerificationAnalyzer;
        this.verificationPhotoStorage = verificationPhotoStorage;
        this.routineCompletionService = routineCompletionService;
    }

    @Override
    public RoutineVerificationResultResponse verifyPhoto(Long dailyRoutineId, MultipartFile photo) {
        Instant verificationRequestedAt = timeProvider.now();
        Long userId = currentUserProvider.getCurrentUser().id();
        validatePhoto(photo);

        DailyRoutine dailyRoutine = dailyRoutineRepository.findById(dailyRoutineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));
        validateOwnership(dailyRoutine, userId);
        validateTimeWindow(dailyRoutine, verificationRequestedAt);
        validateNotVerified(dailyRoutineId);

        PhotoMissionTemplate missionTemplate = photoMissionTemplate(dailyRoutine);
        StoredVerificationPhoto storedPhoto = verificationPhotoStorage.store(photo);
        PhotoVerificationInput analyzerInput = null;
        try {
            analyzerInput = analyzerInput(storedPhoto, dailyRoutine, missionTemplate);
            PhotoVerificationAnalysis analysis = photoVerificationAnalyzer.analyze(analyzerInput);
            validateAnalysis(analysis);
            return routineCompletionService.complete(
                    userId,
                    dailyRoutineId,
                    VerificationType.PHOTO,
                    verificationRequestedAt
            );
        } catch (PhotoVerificationAnalyzerException exception) {
            throw new BusinessException(ErrorCode.PHOTO_AI_UNAVAILABLE);
        } finally {
            if (analyzerInput != null) {
                analyzerInput.destroy();
            }
            verificationPhotoStorage.delete(storedPhoto);
        }
    }

    @Override
    public RoutineVerificationResultResponse verifyCheck(Long dailyRoutineId) {
        Instant verificationRequestedAt = timeProvider.now();
        Long userId = currentUserProvider.getCurrentUser().id();
        return routineCompletionService.complete(
                userId,
                dailyRoutineId,
                VerificationType.CHECK,
                verificationRequestedAt
        );
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!SUPPORTED_PHOTO_CONTENT_TYPES.contains(photo.getContentType())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (photo.getSize() > PhotoVerificationInput.MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private PhotoVerificationInput analyzerInput(
            StoredVerificationPhoto storedPhoto,
            DailyRoutine dailyRoutine,
            PhotoMissionTemplate missionTemplate
    ) {
        byte[] image = storedPhoto.image();
        try {
            return new PhotoVerificationInput(
                    image,
                    storedPhoto.mediaType(),
                    dailyRoutine.getVerificationObjectSnapshot(),
                    missionTemplate.getGestureCode()
            );
        } finally {
            Arrays.fill(image, (byte) 0);
        }
    }

    private void validateOwnership(DailyRoutine dailyRoutine, Long userId) {
        if (!dailyRoutine.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND);
        }
    }

    private void validateTimeWindow(DailyRoutine dailyRoutine, Instant requestedAt) {
        Instant actualStartAt = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getStartTimeSnapshot())
                .atZone(timeProvider.serviceZone())
                .toInstant();
        Instant actualEndAtExclusive = LocalDateTime.of(
                        dailyRoutine.getServiceDate(),
                        dailyRoutine.getEndTimeSnapshot()
                )
                .plusMinutes(1)
                .atZone(timeProvider.serviceZone())
                .toInstant();

        if (requestedAt.isBefore(actualStartAt)) {
            throw new BusinessException(ErrorCode.ROUTINE_NOT_STARTED);
        }
        if (!requestedAt.isBefore(actualEndAtExclusive)) {
            throw new BusinessException(ErrorCode.ROUTINE_WINDOW_CLOSED);
        }
    }

    private void validateNotVerified(Long dailyRoutineId) {
        if (verificationRepository.findByDailyRoutineId(dailyRoutineId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED);
        }
    }

    private PhotoMissionTemplate photoMissionTemplate(DailyRoutine dailyRoutine) {
        Long missionTemplateId = dailyRoutine.getMissionTemplateId();
        if (missionTemplateId == null) {
            throw new BusinessException(ErrorCode.PHOTO_MISSION_NOT_PREPARED);
        }
        return photoMissionTemplateRepository.findById(missionTemplateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_MISSION_NOT_PREPARED));
    }

    private void validateAnalysis(PhotoVerificationAnalysis analysis) {
        if (!analysis.decidable()) {
            throw new BusinessException(ErrorCode.PHOTO_NOT_DECIDABLE);
        }
        if (!analysis.objectDetected() || !analysis.gestureDetected()) {
            throw new BusinessException(ErrorCode.PHOTO_VERIFICATION_FAILED);
        }
    }
}
