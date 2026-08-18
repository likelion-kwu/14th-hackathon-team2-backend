package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRoutineScheduleCoordinator implements RoutineScheduleCoordinator {

    private final DailyRoutineMaterializationService materializationService;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository routineVerificationRepository;

    public DefaultRoutineScheduleCoordinator(
            DailyRoutineMaterializationService materializationService,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository routineVerificationRepository
    ) {
        this.materializationService = materializationService;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.routineVerificationRepository = routineVerificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isServiceDateLocked(Long userId, LocalDate serviceDate) {
        List<Long> dailyRoutineIds = dailyRoutineRepository
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(userId, serviceDate)
                .stream()
                .map(DailyRoutine::getId)
                .toList();
        return !dailyRoutineIds.isEmpty()
                && !routineVerificationRepository.findByDailyRoutineIdIn(dailyRoutineIds).isEmpty();
    }

    @Override
    @Transactional
    public void preserveHistory(Long routineId, LocalDate throughDate) {
        materializationService.ensureMaterializedForRoutine(routineId, throughDate);
    }

    @Override
    @Transactional
    public void synchronizeUpdatedRoutine(Long routineId, Long userId, LocalDate resynchronizeFrom) {
        removeMutableDailyRoutines(routineId, userId, resynchronizeFrom);
        materializationService.ensureMaterializedForUser(userId);
    }

    @Override
    @Transactional
    public void synchronizeDeletedRoutine(Long routineId, Long userId, LocalDate deleteFrom) {
        removeMutableDailyRoutines(routineId, userId, deleteFrom);
    }

    private void removeMutableDailyRoutines(Long routineId, Long userId, LocalDate fromDate) {
        List<DailyRoutine> candidates = dailyRoutineRepository
                .findByRoutineIdAndServiceDateGreaterThanEqualOrderByServiceDateAsc(routineId, fromDate);
        if (candidates.isEmpty()) {
            return;
        }

        LocalDate toDate = candidates.get(candidates.size() - 1).getServiceDate();
        Map<Long, LocalDate> userDailyRoutineDates = userDailyRoutineDates(userId, fromDate, toDate);
        Set<Long> verifiedDailyRoutineIds = verifiedDailyRoutineIds(userDailyRoutineDates.keySet());
        Set<LocalDate> lockedDates = new HashSet<>();
        for (Long verifiedDailyRoutineId : verifiedDailyRoutineIds) {
            LocalDate lockedDate = userDailyRoutineDates.get(verifiedDailyRoutineId);
            if (lockedDate != null) {
                lockedDates.add(lockedDate);
            }
        }

        List<DailyRoutine> removable = candidates.stream()
                .filter(candidate -> !verifiedDailyRoutineIds.contains(candidate.getId()))
                .filter(candidate -> !lockedDates.contains(candidate.getServiceDate()))
                .toList();
        if (!removable.isEmpty()) {
            dailyRoutineRepository.deleteAllInBatch(removable);
        }
    }

    private Map<Long, LocalDate> userDailyRoutineDates(Long userId, LocalDate fromDate, LocalDate toDate) {
        Map<Long, LocalDate> dates = new HashMap<>();
        for (DailyRoutine dailyRoutine : dailyRoutineRepository
                .findByUserIdAndServiceDateBetween(userId, fromDate, toDate)) {
            dates.put(dailyRoutine.getId(), dailyRoutine.getServiceDate());
        }
        return dates;
    }

    private Set<Long> verifiedDailyRoutineIds(Set<Long> dailyRoutineIds) {
        if (dailyRoutineIds.isEmpty()) {
            return Set.of();
        }
        return routineVerificationRepository.findByDailyRoutineIdIn(dailyRoutineIds).stream()
                .map(RoutineVerification::getDailyRoutineId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
