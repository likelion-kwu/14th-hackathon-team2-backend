package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import com.likelion.hackathon_be.routine.dto.PhotoMissionDetailResponse;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultPhotoMissionService implements PhotoMissionService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final PhotoMissionTemplateRepository photoMissionTemplateRepository;
    private final PhotoMissionSelector photoMissionSelector;
    private final TimeProvider timeProvider;

    public DefaultPhotoMissionService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            DailyRoutineRepository dailyRoutineRepository,
            PhotoMissionTemplateRepository photoMissionTemplateRepository,
            PhotoMissionSelector photoMissionSelector,
            TimeProvider timeProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.photoMissionTemplateRepository = photoMissionTemplateRepository;
        this.photoMissionSelector = photoMissionSelector;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional
    public PhotoMissionResponse preparePhotoMission(Long dailyRoutineId) {
        Long userId = currentUserProvider.getCurrentUser().id();
        lockCurrentUser(userId);

        DailyRoutine dailyRoutine = dailyRoutineRepository
                .findOwnedByIdForUpdate(dailyRoutineId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));

        PhotoMissionTemplate template = dailyRoutine.getMissionTemplateId() == null
                ? assignMission(dailyRoutine, userId)
                : findAssignedTemplate(dailyRoutine.getMissionTemplateId());
        return toResponse(dailyRoutine, template);
    }

    private void lockCurrentUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));
    }

    private PhotoMissionTemplate assignMission(DailyRoutine dailyRoutine, Long userId) {
        List<PhotoMissionTemplate> activeTemplates = photoMissionTemplateRepository
                .findByActiveTrueOrderByIdAsc();
        if (activeTemplates.isEmpty()) {
            throw new BusinessException(ErrorCode.PHOTO_MISSION_NOT_PREPARED);
        }

        Long previousTemplateId = dailyRoutineRepository
                .findFirstByUserIdAndIdNotAndMissionTemplateIdIsNotNullOrderByUpdatedAtDescIdDesc(
                        userId,
                        dailyRoutine.getId()
                )
                .map(DailyRoutine::getMissionTemplateId)
                .orElse(null);
        PhotoMissionTemplate selected = photoMissionSelector.select(activeTemplates, previousTemplateId);
        dailyRoutine.assignMissionTemplate(selected.getId(), timeProvider.now());
        dailyRoutineRepository.save(dailyRoutine);
        return selected;
    }

    private PhotoMissionTemplate findAssignedTemplate(Long missionTemplateId) {
        return photoMissionTemplateRepository.findById(missionTemplateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHOTO_MISSION_NOT_PREPARED));
    }

    private PhotoMissionResponse toResponse(
            DailyRoutine dailyRoutine,
            PhotoMissionTemplate template
    ) {
        return new PhotoMissionResponse(
                dailyRoutine.getId(),
                dailyRoutine.getVerificationObjectSnapshot(),
                new PhotoMissionDetailResponse(
                        template.getId(),
                        template.getGestureCode(),
                        template.getInstructionTemplate()
                ),
                actualEndAtExclusive(dailyRoutine)
        );
    }

    private OffsetDateTime actualEndAtExclusive(DailyRoutine dailyRoutine) {
        LocalDateTime endAtExclusive = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getEndTimeSnapshot())
                .plusMinutes(1);
        return endAtExclusive.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
