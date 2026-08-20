package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DefaultStreakAnalysisService implements StreakAnalysisService {

    private final TimeProvider timeProvider;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final DailySuccessRecordRepository dailySuccessRecordRepository;
    private final RoutineVerificationRepository verificationRepository;

    public DefaultStreakAnalysisService(
            TimeProvider timeProvider,
            DailyRoutineRepository dailyRoutineRepository,
            DailySuccessRecordRepository dailySuccessRecordRepository,
            RoutineVerificationRepository verificationRepository
    ) {
        this.timeProvider = timeProvider;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.dailySuccessRecordRepository = dailySuccessRecordRepository;
        this.verificationRepository = verificationRepository;
    }

    @Override
    public StreakAnalysis analyze(Long userId) {
        LocalDate today = timeProvider.todayServiceDate();
        List<LocalDate> scheduledDates = dailyRoutineRepository
                .findScheduledServiceDatesByUserIdThroughDateExcludingCategory(
                        userId,
                        today,
                        RoutineCategory.TO_DO
                );
        Set<LocalDate> successDates = new HashSet<>(
                dailySuccessRecordRepository.findServiceDatesByUserIdThroughDate(userId, today)
        );

        int currentStreak = 0;
        int maxAchievedStreak = 0;
        for (LocalDate scheduledDate : scheduledDates) {
            if (successDates.contains(scheduledDate)) {
                currentStreak += 1;
                maxAchievedStreak = Math.max(maxAchievedStreak, currentStreak);
                continue;
            }
            if (scheduledDate.isBefore(today) || todayHasConfirmedFailure(userId, scheduledDate)) {
                currentStreak = 0;
            }
        }

        long totalSuccessDays = dailySuccessRecordRepository.countByUserId(userId);
        return new StreakAnalysis(
                Math.toIntExact(totalSuccessDays),
                currentStreak,
                maxAchievedStreak
        );
    }

    private boolean todayHasConfirmedFailure(Long userId, LocalDate serviceDate) {
        if (!serviceDate.equals(timeProvider.todayServiceDate())) {
            return false;
        }
        List<DailyRoutine> dailyRoutines = dailyRoutineRepository
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(userId, serviceDate)
                .stream()
                .filter(dailyRoutine -> dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO)
                .toList();
        if (dailyRoutines.isEmpty()) {
            return false;
        }
        List<Long> dailyRoutineIds = dailyRoutines.stream()
                .map(DailyRoutine::getId)
                .toList();
        Set<Long> verifiedDailyRoutineIds = verificationRepository.findByDailyRoutineIdIn(dailyRoutineIds)
                .stream()
                .map(RoutineVerification::getDailyRoutineId)
                .collect(java.util.stream.Collectors.toSet());
        Instant now = timeProvider.now();
        return dailyRoutines.stream()
                .filter(dailyRoutine -> !verifiedDailyRoutineIds.contains(dailyRoutine.getId()))
                .anyMatch(dailyRoutine -> !now.isBefore(actualEndAtExclusive(dailyRoutine)));
    }

    private Instant actualEndAtExclusive(DailyRoutine dailyRoutine) {
        LocalDateTime endAtExclusive = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getEndTimeSnapshot())
                .plusMinutes(1);
        return endAtExclusive.atZone(timeProvider.serviceZone()).toInstant();
    }
}
