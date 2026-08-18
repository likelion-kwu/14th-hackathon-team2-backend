package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DayResultResponse;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import com.likelion.hackathon_be.routine.dto.VerificationPointClaimResponse;
import com.likelion.hackathon_be.routine.dto.VerificationResponse;
import com.likelion.hackathon_be.routine.dto.VerifiedDailyRoutineResponse;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultRoutineVerificationService implements RoutineVerificationService {

    private static final int CHECK_REWARD_POINTS = 5;
    private static final int DAILY_CLAIM_LIMIT = 3;

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final UserRepository userRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final DailySuccessRecordRepository dailySuccessRecordRepository;
    private final RoutinePointClaimRepository pointClaimRepository;

    public DefaultRoutineVerificationService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            UserRepository userRepository,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            DailySuccessRecordRepository dailySuccessRecordRepository,
            RoutinePointClaimRepository pointClaimRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.userRepository = userRepository;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.dailySuccessRecordRepository = dailySuccessRecordRepository;
        this.pointClaimRepository = pointClaimRepository;
    }

    @Override
    public RoutineVerificationResultResponse verifyPhoto(Long dailyRoutineId, MultipartFile photo) {
        throw new FeatureNotImplementedException("Routine PHOTO verification");
    }

    @Override
    @Transactional
    public RoutineVerificationResultResponse verifyCheck(Long dailyRoutineId) {
        Instant verificationRequestedAt = timeProvider.now();
        Long userId = currentUserProvider.getCurrentUser().id();

        lockUser(userId);

        DailyRoutine target = dailyRoutineRepository.findById(dailyRoutineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));
        List<DailyRoutine> lockedDailyRoutines = dailyRoutineRepository
                .findByUserIdAndServiceDateForUpdateOrderByIdAsc(userId, target.getServiceDate());

        DailyRoutine lockedTarget = findLockedTarget(lockedDailyRoutines, dailyRoutineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));

        validateTimeWindow(lockedTarget, verificationRequestedAt);
        if (verificationRepository.findByDailyRoutineId(dailyRoutineId).isPresent()) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED);
        }

        RoutineVerification verification = saveCheckVerification(dailyRoutineId, verificationRequestedAt);
        DailySuccessResult dailySuccessResult = evaluateDailySuccess(
                userId,
                lockedTarget.getServiceDate(),
                lockedDailyRoutines,
                verification
        );

        return toResponse(lockedTarget, verification, dailySuccessResult);
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private Optional<DailyRoutine> findLockedTarget(List<DailyRoutine> dailyRoutines, Long dailyRoutineId) {
        return dailyRoutines.stream()
                .filter(dailyRoutine -> dailyRoutine.getId().equals(dailyRoutineId))
                .findFirst();
    }

    private void validateTimeWindow(DailyRoutine dailyRoutine, Instant requestedAt) {
        Instant actualStartAt = actualStartAt(dailyRoutine);
        Instant actualEndAtExclusive = actualEndAtExclusive(dailyRoutine);

        if (requestedAt.isBefore(actualStartAt)) {
            throw new BusinessException(ErrorCode.ROUTINE_NOT_STARTED);
        }
        if (!requestedAt.isBefore(actualEndAtExclusive)) {
            throw new BusinessException(ErrorCode.ROUTINE_WINDOW_CLOSED);
        }
    }

    private RoutineVerification saveCheckVerification(Long dailyRoutineId, Instant verifiedAt) {
        try {
            return verificationRepository.saveAndFlush(
                    RoutineVerification.create(dailyRoutineId, VerificationType.CHECK, verifiedAt)
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED);
        }
    }

    private DailySuccessResult evaluateDailySuccess(
            Long userId,
            LocalDate serviceDate,
            List<DailyRoutine> lockedDailyRoutines,
            RoutineVerification newVerification
    ) {
        List<DailyRoutine> eligibleDailyRoutines = lockedDailyRoutines.stream()
                .filter(dailyRoutine -> dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO)
                .toList();
        int totalCount = eligibleDailyRoutines.size();
        if (totalCount == 0) {
            return new DailySuccessResult(DayStatus.NO_ROUTINE, false, 0, 0);
        }

        List<Long> eligibleIds = eligibleDailyRoutines.stream()
                .map(DailyRoutine::getId)
                .toList();
        Map<Long, RoutineVerification> verificationByDailyRoutineId = verificationRepository
                .findByDailyRoutineIdIn(eligibleIds)
                .stream()
                .collect(Collectors.toMap(RoutineVerification::getDailyRoutineId, Function.identity()));
        verificationByDailyRoutineId.put(newVerification.getDailyRoutineId(), newVerification);

        int completedCount = (int) eligibleIds.stream()
                .filter(verificationByDailyRoutineId::containsKey)
                .count();

        boolean alreadySucceeded = dailySuccessRecordRepository
                .findByUserIdAndServiceDate(userId, serviceDate)
                .isPresent();
        if (alreadySucceeded) {
            return new DailySuccessResult(DayStatus.SUCCESS, false, completedCount, totalCount);
        }

        if (completedCount == totalCount) {
            dailySuccessRecordRepository.saveAndFlush(
                    DailySuccessRecord.create(userId, serviceDate, newVerification.getVerifiedAt())
            );
            return new DailySuccessResult(DayStatus.SUCCESS, true, completedCount, totalCount);
        }

        return new DailySuccessResult(DayStatus.IN_PROGRESS, false, completedCount, totalCount);
    }

    private RoutineVerificationResultResponse toResponse(
            DailyRoutine dailyRoutine,
            RoutineVerification verification,
            DailySuccessResult dailySuccessResult
    ) {
        return new RoutineVerificationResultResponse(
                new VerificationResponse(
                        verification.getId(),
                        verification.getDailyRoutineId(),
                        verification.getVerificationType().name(),
                        toOffsetDateTime(verification.getVerifiedAt())
                ),
                new VerifiedDailyRoutineResponse(DailyRoutineStatus.COMPLETED),
                new DayResultResponse(
                        dailyRoutine.getServiceDate(),
                        dailySuccessResult.dayStatus(),
                        dailySuccessResult.newlySucceeded(),
                        dailySuccessResult.completedCount(),
                        dailySuccessResult.totalCount()
                ),
                null,
                new VerificationPointClaimResponse(
                        false,
                        isCheckPointClaimable(dailyRoutine),
                        CHECK_REWARD_POINTS
                ),
                null,
                null
        );
    }

    private boolean isCheckPointClaimable(DailyRoutine dailyRoutine) {
        return dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO
                && dailyRoutine.getServiceDate().equals(timeProvider.todayServiceDate())
                && !pointClaimRepository.existsByDailyRoutineId(dailyRoutine.getId())
                && todayClaimCount(dailyRoutine.getUserId()) < DAILY_CLAIM_LIMIT;
    }

    private long todayClaimCount(Long userId) {
        LocalDate today = timeProvider.todayServiceDate();
        ZoneId zone = timeProvider.serviceZone();
        Instant fromInclusive = today.atStartOfDay(zone).toInstant();
        Instant toExclusive = today.plusDays(1).atStartOfDay(zone).toInstant();
        return pointClaimRepository.countByUserIdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(
                userId,
                fromInclusive,
                toExclusive
        );
    }

    private Instant actualStartAt(DailyRoutine dailyRoutine) {
        return dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getStartTimeSnapshot())
                .atZone(timeProvider.serviceZone())
                .toInstant();
    }

    private Instant actualEndAtExclusive(DailyRoutine dailyRoutine) {
        LocalDateTime endAtExclusive = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getEndTimeSnapshot())
                .plusMinutes(1);
        return endAtExclusive.atZone(timeProvider.serviceZone()).toInstant();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        ZoneId zone = timeProvider.serviceZone();
        return instant.atZone(zone).toOffsetDateTime();
    }

    private record DailySuccessResult(
            DayStatus dayStatus,
            boolean newlySucceeded,
            int completedCount,
            int totalCount
    ) {
    }
}
