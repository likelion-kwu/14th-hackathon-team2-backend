package com.likelion.hackathon_be.record.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.record.dto.RecordDayResponse;
import com.likelion.hackathon_be.record.dto.RecordPeriodResponse;
import com.likelion.hackathon_be.record.dto.RecordResponse;
import com.likelion.hackathon_be.record.dto.RecordRoutineResponse;
import com.likelion.hackathon_be.record.dto.RecordSummaryResponse;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.story.application.StreakAnalysis;
import com.likelion.hackathon_be.story.application.StreakAnalysisService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRecordService implements RecordService {

    private static final int MAX_RANGE_DAYS = 31;

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final DailyRoutineMaterializationService materializationService;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final DailySuccessRecordRepository dailySuccessRecordRepository;
    private final StreakAnalysisService streakAnalysisService;

    public DefaultRecordService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            DailyRoutineMaterializationService materializationService,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            DailySuccessRecordRepository dailySuccessRecordRepository,
            StreakAnalysisService streakAnalysisService
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.materializationService = materializationService;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.dailySuccessRecordRepository = dailySuccessRecordRepository;
        this.streakAnalysisService = streakAnalysisService;
    }

    @Override
    @Transactional
    public RecordResponse getRecords(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        Long userId = currentUserProvider.getCurrentUser().id();
        Instant now = timeProvider.now();

        materializationService.ensureMaterializedForUser(userId);

        List<DailyRoutine> dailyRoutines = dailyRoutineRepository
                .findByUserIdAndServiceDateBetweenOrderByServiceDateDescStartTimeSnapshotAscIdAsc(
                        userId,
                        fromDate,
                        toDate
                );
        Map<Long, RoutineVerification> verificationByDailyRoutineId = verificationByDailyRoutineId(dailyRoutines);
        Set<LocalDate> successDates = new HashSet<>(
                dailySuccessRecordRepository.findServiceDatesByUserIdAndServiceDateBetween(userId, fromDate, toDate)
        );
        StreakAnalysis streak = streakAnalysisService.analyze(userId);

        return new RecordResponse(
                new RecordPeriodResponse(fromDate, toDate),
                summary(dailyRoutines, verificationByDailyRoutineId, streak),
                days(fromDate, toDate, dailyRoutines, verificationByDailyRoutineId, successDates, now)
        );
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fromDate and toDate are required.");
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "fromDate must be before or equal to toDate.");
        }
        long daysInclusive = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (daysInclusive > MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Record range cannot exceed 31 days.");
        }
    }

    private Map<Long, RoutineVerification> verificationByDailyRoutineId(List<DailyRoutine> dailyRoutines) {
        List<Long> dailyRoutineIds = dailyRoutines.stream()
                .map(DailyRoutine::getId)
                .toList();
        if (dailyRoutineIds.isEmpty()) {
            return Map.of();
        }
        return verificationRepository.findByDailyRoutineIdIn(dailyRoutineIds)
                .stream()
                .collect(Collectors.toMap(RoutineVerification::getDailyRoutineId, Function.identity()));
    }

    private RecordSummaryResponse summary(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verificationByDailyRoutineId,
            StreakAnalysis streak
    ) {
        List<DailyRoutine> eligibleDailyRoutines = eligibleDailyRoutines(dailyRoutines);
        int scheduledRoutineCount = eligibleDailyRoutines.size();
        int completedRoutineCount = (int) eligibleDailyRoutines.stream()
                .filter(dailyRoutine -> verificationByDailyRoutineId.containsKey(dailyRoutine.getId()))
                .count();
        int photoVerificationCount = verificationCount(
                eligibleDailyRoutines,
                verificationByDailyRoutineId,
                VerificationType.PHOTO
        );
        int checkVerificationCount = verificationCount(
                eligibleDailyRoutines,
                verificationByDailyRoutineId,
                VerificationType.CHECK
        );

        return new RecordSummaryResponse(
                scheduledRoutineCount,
                completedRoutineCount,
                percentage(completedRoutineCount, scheduledRoutineCount),
                photoVerificationCount,
                checkVerificationCount,
                streak.totalSuccessDays(),
                streak.currentStreakDays(),
                streak.maxAchievedStreakDays()
        );
    }

    private int verificationCount(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verificationByDailyRoutineId,
            VerificationType verificationType
    ) {
        return (int) dailyRoutines.stream()
                .map(dailyRoutine -> verificationByDailyRoutineId.get(dailyRoutine.getId()))
                .filter(verification -> verification != null
                        && verification.getVerificationType() == verificationType)
                .count();
    }

    private List<RecordDayResponse> days(
            LocalDate fromDate,
            LocalDate toDate,
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verificationByDailyRoutineId,
            Set<LocalDate> successDates,
            Instant now
    ) {
        Map<LocalDate, List<DailyRoutine>> dailyRoutinesByDate = dailyRoutines.stream()
                .collect(Collectors.groupingBy(
                        DailyRoutine::getServiceDate,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<RecordDayResponse> responses = new ArrayList<>();
        for (LocalDate date = toDate; !date.isBefore(fromDate); date = date.minusDays(1)) {
            List<DailyRoutine> dateRoutines = dailyRoutinesByDate.getOrDefault(date, List.of());
            responses.add(day(date, dateRoutines, verificationByDailyRoutineId, successDates, now));
        }
        return responses;
    }

    private RecordDayResponse day(
            LocalDate serviceDate,
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verificationByDailyRoutineId,
            Set<LocalDate> successDates,
            Instant now
    ) {
        List<DailyRoutine> eligibleDailyRoutines = eligibleDailyRoutines(dailyRoutines);
        int totalCount = eligibleDailyRoutines.size();
        int completedCount = (int) eligibleDailyRoutines.stream()
                .filter(dailyRoutine -> verificationByDailyRoutineId.containsKey(dailyRoutine.getId()))
                .count();

        return new RecordDayResponse(
                serviceDate,
                dayStatus(eligibleDailyRoutines, verificationByDailyRoutineId, successDates, now).name(),
                completedCount,
                totalCount,
                dailyRoutines.stream()
                        .map(dailyRoutine -> routine(dailyRoutine, verificationByDailyRoutineId.get(dailyRoutine.getId()), now))
                        .toList()
        );
    }

    private DayStatus dayStatus(
            List<DailyRoutine> eligibleDailyRoutines,
            Map<Long, RoutineVerification> verificationByDailyRoutineId,
            Set<LocalDate> successDates,
            Instant now
    ) {
        if (eligibleDailyRoutines.isEmpty()) {
            return DayStatus.NO_ROUTINE;
        }
        LocalDate serviceDate = eligibleDailyRoutines.get(0).getServiceDate();
        if (successDates.contains(serviceDate)) {
            return DayStatus.SUCCESS;
        }
        boolean hasFailedRoutine = eligibleDailyRoutines.stream()
                .filter(dailyRoutine -> !verificationByDailyRoutineId.containsKey(dailyRoutine.getId()))
                .anyMatch(dailyRoutine -> !now.isBefore(actualEndAtExclusive(dailyRoutine)));
        return hasFailedRoutine ? DayStatus.FAILED : DayStatus.IN_PROGRESS;
    }

    private RecordRoutineResponse routine(
            DailyRoutine dailyRoutine,
            RoutineVerification verification,
            Instant now
    ) {
        return new RecordRoutineResponse(
                dailyRoutine.getId(),
                dailyRoutine.getRoutineId(),
                dailyRoutine.getContentSnapshot(),
                status(dailyRoutine, verification, now).name(),
                verification == null ? null : verification.getVerificationType().name()
        );
    }

    private DailyRoutineStatus status(DailyRoutine dailyRoutine, RoutineVerification verification, Instant now) {
        if (verification != null) {
            return DailyRoutineStatus.COMPLETED;
        }
        Instant actualStartAt = actualStartAt(dailyRoutine);
        Instant actualEndAtExclusive = actualEndAtExclusive(dailyRoutine);
        if (now.isBefore(actualStartAt)) {
            return DailyRoutineStatus.UPCOMING;
        }
        if (now.isBefore(actualEndAtExclusive)) {
            return DailyRoutineStatus.AVAILABLE;
        }
        return DailyRoutineStatus.FAILED;
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

    private List<DailyRoutine> eligibleDailyRoutines(List<DailyRoutine> dailyRoutines) {
        return dailyRoutines.stream()
                .filter(dailyRoutine -> dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO)
                .toList();
    }

    private int percentage(int completedCount, int totalCount) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.round(completedCount * 100.0 / totalCount);
    }
}
