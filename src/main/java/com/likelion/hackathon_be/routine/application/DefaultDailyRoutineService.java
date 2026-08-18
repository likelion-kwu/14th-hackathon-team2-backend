package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DailyRoutineListResponse;
import com.likelion.hackathon_be.routine.dto.DailyRoutinePointClaimStatusResponse;
import com.likelion.hackathon_be.routine.dto.DailyRoutineResponse;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.dto.DailyRoutineVerificationResponse;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultDailyRoutineService implements DailyRoutineService {

    private static final int DAILY_CLAIM_LIMIT = 3;
    private static final int PHOTO_REWARD_POINTS = 10;
    private static final int CHECK_REWARD_POINTS = 5;

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final DailyRoutineMaterializationService materializationService;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final RoutinePointClaimRepository pointClaimRepository;
    private final DailySuccessRecordRepository dailySuccessRecordRepository;

    public DefaultDailyRoutineService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            DailyRoutineMaterializationService materializationService,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            RoutinePointClaimRepository pointClaimRepository,
            DailySuccessRecordRepository dailySuccessRecordRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.materializationService = materializationService;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.pointClaimRepository = pointClaimRepository;
        this.dailySuccessRecordRepository = dailySuccessRecordRepository;
    }

    @Override
    @Transactional
    public DailyRoutineListResponse getDailyRoutines(LocalDate date) {
        Long userId = currentUserProvider.getCurrentUser().id();
        LocalDate serviceDate = date == null ? timeProvider.todayServiceDate() : date;

        materializationService.ensureMaterializedForUser(userId);

        List<DailyRoutine> dailyRoutines = dailyRoutineRepository
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(userId, serviceDate);
        List<Long> dailyRoutineIds = dailyRoutines.stream()
                .map(DailyRoutine::getId)
                .toList();

        Map<Long, RoutineVerification> verifications = mapByDailyRoutineId(
                verificationRepository.findByDailyRoutineIdIn(dailyRoutineIds),
                RoutineVerification::getDailyRoutineId
        );
        Map<Long, RoutinePointClaim> pointClaims = mapByDailyRoutineId(
                pointClaimRepository.findByDailyRoutineIdIn(dailyRoutineIds),
                RoutinePointClaim::getDailyRoutineId
        );

        Instant now = timeProvider.now();
        boolean successRecorded = dailySuccessRecordRepository.existsByUserIdAndServiceDate(userId, serviceDate);
        long todayClaimedCount = countTodayClaims(userId);

        List<DailyRoutineResponse> routineResponses = dailyRoutines.stream()
                .map(dailyRoutine -> toResponse(
                        dailyRoutine,
                        verifications.get(dailyRoutine.getId()),
                        pointClaims.get(dailyRoutine.getId()),
                        now,
                        todayClaimedCount
                ))
                .toList();

        int totalCount = eligibleCount(dailyRoutines);
        int completedCount = completedEligibleCount(dailyRoutines, verifications);
        int percentage = percentage(completedCount, totalCount);
        DayStatus dayStatus = dayStatus(dailyRoutines, verifications, successRecorded, now);

        return new DailyRoutineListResponse(
                serviceDate,
                dayStatus,
                completedCount,
                totalCount,
                percentage,
                routineResponses
        );
    }

    private <T> Map<Long, T> mapByDailyRoutineId(Collection<T> values, Function<T, Long> dailyRoutineIdMapper) {
        return values.stream()
                .collect(Collectors.toMap(dailyRoutineIdMapper, Function.identity()));
    }

    private DailyRoutineResponse toResponse(
            DailyRoutine dailyRoutine,
            RoutineVerification verification,
            RoutinePointClaim pointClaim,
            Instant now,
            long todayClaimedCount
    ) {
        OffsetDateTime actualStartAt = actualStartAt(dailyRoutine);
        OffsetDateTime actualEndAtExclusive = actualEndAtExclusive(dailyRoutine);

        return new DailyRoutineResponse(
                dailyRoutine.getId(),
                dailyRoutine.getRoutineId(),
                dailyRoutine.getCategorySnapshot().name(),
                dailyRoutine.getContentSnapshot(),
                dailyRoutine.getStartTimeSnapshot(),
                dailyRoutine.getEndTimeSnapshot(),
                actualStartAt,
                actualEndAtExclusive,
                dailyRoutine.getVerificationObjectSnapshot(),
                status(verification, now, actualStartAt, actualEndAtExclusive),
                verificationResponse(verification),
                pointClaimResponse(dailyRoutine, verification, pointClaim, todayClaimedCount)
        );
    }

    private DailyRoutineStatus status(
            RoutineVerification verification,
            Instant now,
            OffsetDateTime actualStartAt,
            OffsetDateTime actualEndAtExclusive
    ) {
        if (verification != null) {
            return DailyRoutineStatus.COMPLETED;
        }
        if (now.isBefore(actualStartAt.toInstant())) {
            return DailyRoutineStatus.UPCOMING;
        }
        if (now.isBefore(actualEndAtExclusive.toInstant())) {
            return DailyRoutineStatus.AVAILABLE;
        }
        return DailyRoutineStatus.FAILED;
    }

    private DailyRoutineVerificationResponse verificationResponse(RoutineVerification verification) {
        if (verification == null) {
            return null;
        }
        return new DailyRoutineVerificationResponse(
                verification.getVerificationType().name(),
                toOffsetDateTime(verification.getVerifiedAt())
        );
    }

    private DailyRoutinePointClaimStatusResponse pointClaimResponse(
            DailyRoutine dailyRoutine,
            RoutineVerification verification,
            RoutinePointClaim pointClaim,
            long todayClaimedCount
    ) {
        if (dailyRoutine.getCategorySnapshot() == RoutineCategory.TO_DO || verification == null) {
            return null;
        }

        boolean claimed = pointClaim != null;
        boolean claimable = !claimed
                && dailyRoutine.getServiceDate().equals(timeProvider.todayServiceDate())
                && todayClaimedCount < DAILY_CLAIM_LIMIT;

        return new DailyRoutinePointClaimStatusResponse(
                claimed,
                claimable,
                rewardPoints(verification.getVerificationType())
        );
    }

    private int rewardPoints(VerificationType verificationType) {
        return verificationType == VerificationType.PHOTO ? PHOTO_REWARD_POINTS : CHECK_REWARD_POINTS;
    }

    private int eligibleCount(List<DailyRoutine> dailyRoutines) {
        return (int) dailyRoutines.stream()
                .filter(this::isProgressEligible)
                .count();
    }

    private int completedEligibleCount(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verifications
    ) {
        return (int) dailyRoutines.stream()
                .filter(this::isProgressEligible)
                .filter(dailyRoutine -> verifications.containsKey(dailyRoutine.getId()))
                .count();
    }

    private int percentage(int completedCount, int totalCount) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.round(completedCount * 100.0 / totalCount);
    }

    private DayStatus dayStatus(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verifications,
            boolean successRecorded,
            Instant now
    ) {
        List<DailyRoutine> eligibleRoutines = dailyRoutines.stream()
                .filter(this::isProgressEligible)
                .toList();
        if (eligibleRoutines.isEmpty()) {
            return DayStatus.NO_ROUTINE;
        }
        if (successRecorded) {
            return DayStatus.SUCCESS;
        }

        boolean hasFailedRoutine = eligibleRoutines.stream()
                .filter(dailyRoutine -> !verifications.containsKey(dailyRoutine.getId()))
                .anyMatch(dailyRoutine -> !now.isBefore(actualEndAtExclusive(dailyRoutine).toInstant()));
        if (hasFailedRoutine) {
            return DayStatus.FAILED;
        }
        return DayStatus.IN_PROGRESS;
    }

    private boolean isProgressEligible(DailyRoutine dailyRoutine) {
        return dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO;
    }

    private long countTodayClaims(Long userId) {
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

    private OffsetDateTime actualStartAt(DailyRoutine dailyRoutine) {
        return toOffsetDateTime(dailyRoutine.getServiceDate(), dailyRoutine.getStartTimeSnapshot());
    }

    private OffsetDateTime actualEndAtExclusive(DailyRoutine dailyRoutine) {
        LocalDateTime endAtExclusive = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getEndTimeSnapshot())
                .plusMinutes(1);
        return endAtExclusive.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
