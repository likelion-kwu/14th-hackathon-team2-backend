package com.likelion.hackathon_be.routine.daily.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.repository.RoutineRepeatDayRepository;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultDailyRoutineMaterializationService implements DailyRoutineMaterializationService {

    static final int MATERIALIZATION_HORIZON_DAYS = 60;

    private final RoutineRepository routineRepository;
    private final RoutineRepeatDayRepository routineRepeatDayRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final TimeProvider timeProvider;

    public DefaultDailyRoutineMaterializationService(
            RoutineRepository routineRepository,
            RoutineRepeatDayRepository routineRepeatDayRepository,
            DailyRoutineRepository dailyRoutineRepository,
            TimeProvider timeProvider
    ) {
        this.routineRepository = routineRepository;
        this.routineRepeatDayRepository = routineRepeatDayRepository;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional
    public int ensureMaterializedForUser(Long userId) {
        LocalDate throughDate = timeProvider.todayServiceDate().plusDays(MATERIALIZATION_HORIZON_DAYS);
        return routineRepository
                .findByUserIdAndEffectiveFromLessThanEqualAndDeletedAtIsNullOrderByIdAsc(userId, throughDate)
                .stream()
                .mapToInt(routine -> materialize(routine, throughDate))
                .sum();
    }

    @Override
    @Transactional
    public int ensureMaterializedForRoutine(Long routineId, LocalDate throughDate) {
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_FOUND));
        return materialize(routine, throughDate);
    }

    private int materialize(Routine routine, LocalDate requestedThroughDate) {
        LocalDate fromDate = routine.getEffectiveFrom();
        LocalDate throughDate = effectiveThroughDate(routine, requestedThroughDate);
        if (throughDate.isBefore(fromDate)) {
            return 0;
        }

        Set<LocalDate> existingDates = existingServiceDates(routine.getId(), fromDate, throughDate);
        Set<DayOfWeek> repeatDays = repeatDays(routine);
        Instant now = timeProvider.now();

        List<DailyRoutine> missingDailyRoutines = fromDate.datesUntil(throughDate.plusDays(1))
                .filter(serviceDate -> shouldMaterialize(routine, serviceDate, repeatDays))
                .filter(serviceDate -> !existingDates.contains(serviceDate))
                .map(serviceDate -> DailyRoutine.createSnapshot(routine, serviceDate, now))
                .toList();

        if (missingDailyRoutines.isEmpty()) {
            return 0;
        }
        dailyRoutineRepository.saveAll(missingDailyRoutines);
        return missingDailyRoutines.size();
    }

    private LocalDate effectiveThroughDate(Routine routine, LocalDate requestedThroughDate) {
        LocalDate throughDate = routine.getRepeatType() == RepeatType.ONCE
                ? routine.getEffectiveFrom()
                : requestedThroughDate;

        if (routine.getDeletedAt() == null) {
            return throughDate;
        }

        LocalDate deletedServiceDate = LocalDate.ofInstant(routine.getDeletedAt(), timeProvider.serviceZone());
        if (deletedServiceDate.isBefore(throughDate)) {
            return deletedServiceDate;
        }
        return throughDate;
    }

    private Set<LocalDate> existingServiceDates(Long routineId, LocalDate fromDate, LocalDate throughDate) {
        List<DailyRoutine> existingDailyRoutines = dailyRoutineRepository
                .findByRoutineIdAndServiceDateBetween(routineId, fromDate, throughDate);
        Set<LocalDate> dates = new HashSet<>();
        for (DailyRoutine dailyRoutine : existingDailyRoutines) {
            dates.add(dailyRoutine.getServiceDate());
        }
        return dates;
    }

    private Set<DayOfWeek> repeatDays(Routine routine) {
        if (routine.getRepeatType() != RepeatType.DAYS_OF_WEEK) {
            return EnumSet.noneOf(DayOfWeek.class);
        }

        List<RoutineRepeatDay> repeatDays = routineRepeatDayRepository.findByIdRoutineId(routine.getId());
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (RoutineRepeatDay repeatDay : repeatDays) {
            days.add(repeatDay.getId().getDayOfWeek());
        }
        return days;
    }

    private boolean shouldMaterialize(Routine routine, LocalDate serviceDate, Set<DayOfWeek> repeatDays) {
        return switch (routine.getRepeatType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> repeatDays.contains(DayOfWeek.fromJavaDayOfWeek(serviceDate.getDayOfWeek()));
            case ONCE -> serviceDate.equals(routine.getEffectiveFrom());
        };
    }
}
