package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.application.RoutinePolicyValidator.ValidatedRoutine;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import com.likelion.hackathon_be.routine.repository.RoutineRepeatDayRepository;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRoutineService implements RoutineService {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final RoutineRepository routineRepository;
    private final RoutineRepeatDayRepository routineRepeatDayRepository;
    private final RoutinePolicyValidator routinePolicyValidator;
    private final RoutineScheduleCoordinator routineScheduleCoordinator;
    private final DailyRoutineMaterializationService materializationService;
    private final TimeProvider timeProvider;

    public DefaultRoutineService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            RoutineRepository routineRepository,
            RoutineRepeatDayRepository routineRepeatDayRepository,
            RoutinePolicyValidator routinePolicyValidator,
            RoutineScheduleCoordinator routineScheduleCoordinator,
            DailyRoutineMaterializationService materializationService,
            TimeProvider timeProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.routineRepository = routineRepository;
        this.routineRepeatDayRepository = routineRepeatDayRepository;
        this.routinePolicyValidator = routinePolicyValidator;
        this.routineScheduleCoordinator = routineScheduleCoordinator;
        this.materializationService = materializationService;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutineResponse> getRoutines() {
        Long userId = currentUserId();
        List<Routine> routines = routineRepository.findByUserIdAndDeletedAtIsNullOrderByIdAsc(userId);
        Map<Long, List<DayOfWeek>> repeatDays = repeatDaysByRoutine(routines);
        LocalDate today = timeProvider.todayServiceDate();

        return routines.stream()
                .map(routine -> toResponse(routine, repeatDays.getOrDefault(routine.getId(), List.of()), today))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoutineResponse getRoutine(Long routineId) {
        Routine routine = ownedRoutine(routineId, currentUserId());
        List<DayOfWeek> repeatDays = repeatDays(routine);
        return toResponse(routine, repeatDays, timeProvider.todayServiceDate());
    }

    @Override
    @Transactional
    public RoutineResponse createRoutine(CreateRoutineRequest request) {
        Long userId = currentUserId();
        lockCurrentUser(userId);

        LocalDate today = timeProvider.todayServiceDate();
        Instant now = timeProvider.now();
        LocalTime serviceTime = LocalTime.ofInstant(now, timeProvider.serviceZone());
        ValidatedRoutine validated = routinePolicyValidator.validate(request, today);
        boolean serviceDateLocked = routineScheduleCoordinator.isServiceDateLocked(userId, today);
        LocalDate effectiveFrom = effectiveFromForCreate(validated, today, serviceTime, serviceDateLocked);

        Routine routine = Routine.create(
                userId,
                validated.category(),
                validated.content(),
                validated.startTime(),
                validated.endTime(),
                validated.repeatType(),
                validated.verificationObject(),
                effectiveFrom,
                now
        );
        routineRepository.save(routine);
        saveRepeatDays(routine.getId(), validated.daysOfWeek());
        materializationService.ensureMaterializedForUser(userId);

        return toResponse(routine, validated.daysOfWeek(), today);
    }

    @Override
    @Transactional
    public RoutineResponse updateRoutine(Long routineId, UpdateRoutineRequest request) {
        Long userId = currentUserId();
        lockCurrentUser(userId);
        Routine routine = ownedRoutine(routineId, userId);
        List<DayOfWeek> previousRepeatDays = repeatDays(routine);

        LocalDate today = timeProvider.todayServiceDate();
        Instant now = timeProvider.now();
        LocalTime serviceTime = LocalTime.ofInstant(now, timeProvider.serviceZone());
        routineScheduleCoordinator.preserveHistory(routineId, today);
        ValidatedRoutine validated = routinePolicyValidator.validate(request, today);
        boolean serviceDateLocked = routineScheduleCoordinator.isServiceDateLocked(userId, today);
        UpdateSchedule updateSchedule = updateSchedule(
                routine,
                previousRepeatDays,
                validated,
                today,
                serviceTime,
                serviceDateLocked
        );

        routine.update(
                validated.category(),
                validated.content(),
                validated.startTime(),
                validated.endTime(),
                validated.repeatType(),
                validated.verificationObject(),
                updateSchedule.effectiveFrom(),
                now
        );
        routineRepository.save(routine);
        replaceRepeatDays(routineId, validated.daysOfWeek());
        routineScheduleCoordinator.synchronizeUpdatedRoutine(
                routineId,
                userId,
                updateSchedule.resynchronizeFrom()
        );

        return toResponse(routine, validated.daysOfWeek(), today);
    }

    @Override
    @Transactional
    public void deleteRoutine(Long routineId) {
        Long userId = currentUserId();
        lockCurrentUser(userId);
        Routine routine = ownedRoutine(routineId, userId);
        List<DayOfWeek> repeatDays = repeatDays(routine);

        LocalDate today = timeProvider.todayServiceDate();
        Instant now = timeProvider.now();
        LocalTime serviceTime = LocalTime.ofInstant(now, timeProvider.serviceZone());
        routineScheduleCoordinator.preserveHistory(routineId, today);
        boolean appliesToday = appliesOn(routine, repeatDays, today);
        boolean serviceDateLocked = appliesToday
                && routineScheduleCoordinator.isServiceDateLocked(userId, today);
        boolean protectToday = appliesToday
                && (serviceDateLocked || !routine.getStartTime().isAfter(serviceTime));
        LocalDate deleteFrom = protectToday ? today.plusDays(1) : today;

        routine.softDelete(now);
        routineRepository.save(routine);
        routineScheduleCoordinator.synchronizeDeletedRoutine(routineId, userId, deleteFrom);
    }

    private LocalDate effectiveFromForCreate(
            ValidatedRoutine routine,
            LocalDate today,
            LocalTime serviceTime,
            boolean serviceDateLocked
    ) {
        if (routine.repeatType() == RepeatType.ONCE) {
            validateTodayOnceChange(routine, today, serviceTime, serviceDateLocked, false);
            return routine.scheduledDate();
        }

        boolean appliesToday = appliesOn(routine, today);
        boolean protectToday = appliesToday
                && (serviceDateLocked || !routine.startTime().isAfter(serviceTime));
        return nextApplicableDate(routine, protectToday ? today.plusDays(1) : today);
    }

    private UpdateSchedule updateSchedule(
            Routine previous,
            List<DayOfWeek> previousRepeatDays,
            ValidatedRoutine updated,
            LocalDate today,
            LocalTime serviceTime,
            boolean serviceDateLocked
    ) {
        boolean previousAppliesToday = appliesOn(previous, previousRepeatDays, today);
        boolean updatedAppliesToday = appliesOn(updated, today);
        boolean affectsToday = previousAppliesToday || updatedAppliesToday;
        boolean protectToday = affectsToday && (
                serviceDateLocked
                        || previousAppliesToday && !previous.getStartTime().isAfter(serviceTime)
                        || updatedAppliesToday && !updated.startTime().isAfter(serviceTime)
        );

        if (updated.repeatType() == RepeatType.ONCE && updated.scheduledDate().equals(today)) {
            validateTodayOnceChange(
                    updated,
                    today,
                    serviceTime,
                    serviceDateLocked,
                    previousAppliesToday && !previous.getStartTime().isAfter(serviceTime)
            );
        }

        LocalDate mutableFrom = protectToday ? today.plusDays(1) : today;
        LocalDate effectiveFrom = updated.repeatType() == RepeatType.ONCE
                ? updated.scheduledDate()
                : nextApplicableDate(updated, mutableFrom);
        LocalDate earliestAffected = previous.getEffectiveFrom().isBefore(effectiveFrom)
                ? previous.getEffectiveFrom()
                : effectiveFrom;
        LocalDate resynchronizeFrom = earliestAffected.isBefore(mutableFrom)
                ? mutableFrom
                : earliestAffected;
        return new UpdateSchedule(effectiveFrom, resynchronizeFrom);
    }

    private void validateTodayOnceChange(
            ValidatedRoutine routine,
            LocalDate today,
            LocalTime serviceTime,
            boolean serviceDateLocked,
            boolean previousRoutineAlreadyStarted
    ) {
        if (!routine.scheduledDate().equals(today)) {
            return;
        }
        if (serviceDateLocked) {
            throw new BusinessException(ErrorCode.SERVICE_DATE_LOCKED);
        }
        if (previousRoutineAlreadyStarted || !routine.startTime().isAfter(serviceTime)) {
            throw new BusinessException(ErrorCode.INVALID_ONCE_DATE);
        }
    }

    private LocalDate nextApplicableDate(ValidatedRoutine routine, LocalDate fromDate) {
        if (routine.repeatType() == RepeatType.DAILY) {
            return fromDate;
        }
        for (int offset = 0; offset < 7; offset++) {
            LocalDate candidate = fromDate.plusDays(offset);
            if (appliesOn(routine, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_REPEAT_DAYS);
    }

    private boolean appliesOn(ValidatedRoutine routine, LocalDate serviceDate) {
        return switch (routine.repeatType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> routine.daysOfWeek()
                    .contains(DayOfWeek.fromJavaDayOfWeek(serviceDate.getDayOfWeek()));
            case ONCE -> routine.scheduledDate().equals(serviceDate);
        };
    }

    private boolean appliesOn(Routine routine, List<DayOfWeek> repeatDays, LocalDate serviceDate) {
        if (routine.getEffectiveFrom().isAfter(serviceDate)) {
            return false;
        }
        return switch (routine.getRepeatType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> repeatDays.contains(
                    DayOfWeek.fromJavaDayOfWeek(serviceDate.getDayOfWeek())
            );
            case ONCE -> routine.getEffectiveFrom().equals(serviceDate);
        };
    }

    private void replaceRepeatDays(Long routineId, List<DayOfWeek> daysOfWeek) {
        routineRepeatDayRepository.deleteAllByRoutineId(routineId);
        saveRepeatDays(routineId, daysOfWeek);
    }

    private void saveRepeatDays(Long routineId, List<DayOfWeek> daysOfWeek) {
        if (daysOfWeek.isEmpty()) {
            return;
        }
        routineRepeatDayRepository.saveAll(daysOfWeek.stream()
                .map(day -> RoutineRepeatDay.of(routineId, day))
                .toList());
    }

    private Map<Long, List<DayOfWeek>> repeatDaysByRoutine(List<Routine> routines) {
        List<Long> routineIds = routines.stream().map(Routine::getId).toList();
        if (routineIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, EnumSet<DayOfWeek>> grouped = new HashMap<>();
        for (RoutineRepeatDay repeatDay : routineRepeatDayRepository.findByIdRoutineIdIn(routineIds)) {
            grouped.computeIfAbsent(
                    repeatDay.getId().getRoutineId(),
                    ignored -> EnumSet.noneOf(DayOfWeek.class)
            ).add(repeatDay.getId().getDayOfWeek());
        }

        Map<Long, List<DayOfWeek>> result = new HashMap<>();
        grouped.forEach((routineId, days) -> result.put(routineId, List.copyOf(new ArrayList<>(days))));
        return result;
    }

    private List<DayOfWeek> repeatDays(Routine routine) {
        if (routine.getRepeatType() != RepeatType.DAYS_OF_WEEK) {
            return List.of();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (RoutineRepeatDay repeatDay : routineRepeatDayRepository.findByIdRoutineId(routine.getId())) {
            days.add(repeatDay.getId().getDayOfWeek());
        }
        return List.copyOf(new ArrayList<>(days));
    }

    private RoutineResponse toResponse(Routine routine, List<DayOfWeek> repeatDays, LocalDate today) {
        return new RoutineResponse(
                routine.getId(),
                routine.getCategory().name(),
                routine.getContent(),
                routine.getRepeatType() == RepeatType.ONCE ? routine.getEffectiveFrom() : null,
                routine.getStartTime(),
                routine.getEndTime(),
                routine.getRepeatType().name(),
                routine.getRepeatType() == RepeatType.DAYS_OF_WEEK
                        ? repeatDays.stream().map(Enum::name).toList()
                        : List.of(),
                routine.getVerificationObject(),
                routine.getEffectiveFrom(),
                appliesOn(routine, repeatDays, today),
                toOffsetDateTime(routine.getCreatedAt()),
                toOffsetDateTime(routine.getUpdatedAt())
        );
    }

    private Routine ownedRoutine(Long routineId, Long userId) {
        if (routineId == null) {
            throw new BusinessException(ErrorCode.ROUTINE_NOT_FOUND);
        }
        return routineRepository.findByIdAndUserIdAndDeletedAtIsNull(routineId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_FOUND));
    }

    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().id();
    }

    private void lockCurrentUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }

    private record UpdateSchedule(
            LocalDate effectiveFrom,
            LocalDate resynchronizeFrom
    ) {
    }
}
