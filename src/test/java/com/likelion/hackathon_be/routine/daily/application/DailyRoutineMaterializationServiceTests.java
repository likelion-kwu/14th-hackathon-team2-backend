package com.likelion.hackathon_be.routine.daily.application;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.repository.RoutineRepeatDayRepository;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyRoutineMaterializationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private RoutineRepository routineRepository;
    private RoutineRepeatDayRepository repeatDayRepository;
    private DailyRoutineRepository dailyRoutineRepository;
    private DefaultDailyRoutineMaterializationService service;

    @BeforeEach
    void setUp() {
        routineRepository = mock(RoutineRepository.class);
        repeatDayRepository = mock(RoutineRepeatDayRepository.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        service = new DefaultDailyRoutineMaterializationService(
                routineRepository,
                repeatDayRepository,
                dailyRoutineRepository,
                new FixedTimeProvider(NOW)
        );
    }

    @Test
    void dailyRoutineMaterializesEveryDateInRange() {
        Routine routine = routine(1L, 10L, RoutineCategory.SKIN, RepeatType.DAILY, TODAY);
        when(routineRepository.findById(1L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(1L, TODAY, TODAY.plusDays(2)))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForRoutine(1L, TODAY.plusDays(2));

        assertThat(created).isEqualTo(3);
        assertThat(savedDates()).containsExactly(TODAY, TODAY.plusDays(1), TODAY.plusDays(2));
    }

    @Test
    void daysOfWeekRoutineMaterializesOnlyConfiguredDays() {
        Routine routine = routine(2L, 10L, RoutineCategory.HEALTH_FIT, RepeatType.DAYS_OF_WEEK, TODAY);
        when(routineRepository.findById(2L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(2L, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of());
        when(repeatDayRepository.findByIdRoutineId(2L))
                .thenReturn(List.of(RoutineRepeatDay.of(2L, DayOfWeek.WED), RoutineRepeatDay.of(2L, DayOfWeek.FRI)));

        int created = service.ensureMaterializedForRoutine(2L, TODAY.plusDays(6));

        assertThat(created).isEqualTo(2);
        assertThat(savedDates()).containsExactly(TODAY, TODAY.plusDays(2));
    }

    @Test
    void onceRoutineMaterializesOnlyEffectiveFrom() {
        Routine routine = routine(3L, 10L, RoutineCategory.DIET, RepeatType.ONCE, TODAY.plusDays(5));
        when(routineRepository.findById(3L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(3L, TODAY.plusDays(5), TODAY.plusDays(5)))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForRoutine(3L, TODAY.plusDays(60));

        assertThat(created).isEqualTo(1);
        assertThat(savedDates()).containsExactly(TODAY.plusDays(5));
    }

    @Test
    void todoOnceRoutineMaterializesOneDailyRoutine() {
        Routine routine = routine(4L, 10L, RoutineCategory.TO_DO, RepeatType.ONCE, TODAY.plusDays(1));
        when(routineRepository.findById(4L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(4L, TODAY.plusDays(1), TODAY.plusDays(1)))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForRoutine(4L, TODAY.plusDays(60));

        assertThat(created).isEqualTo(1);
        DailyRoutine saved = savedDailyRoutines().get(0);
        assertThat(saved.getServiceDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(saved.getCategorySnapshot()).isEqualTo(RoutineCategory.TO_DO);
    }

    @Test
    void snapshotCopiesRoutineFields() {
        Routine routine = routine(5L, 20L, RoutineCategory.WELL_BEING, RepeatType.DAILY, TODAY);
        when(routineRepository.findById(5L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(5L, TODAY, TODAY))
                .thenReturn(List.of());

        service.ensureMaterializedForRoutine(5L, TODAY);

        DailyRoutine saved = savedDailyRoutines().get(0);
        assertThat(saved.getRoutineId()).isEqualTo(5L);
        assertThat(saved.getUserId()).isEqualTo(20L);
        assertThat(saved.getCategorySnapshot()).isEqualTo(RoutineCategory.WELL_BEING);
        assertThat(saved.getContentSnapshot()).isEqualTo("routine-5");
        assertThat(saved.getStartTimeSnapshot()).isEqualTo(LocalTime.of(7, 0));
        assertThat(saved.getEndTimeSnapshot()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.getVerificationObjectSnapshot()).isEqualTo("object-5");
        assertThat(saved.getMissionTemplateId()).isNull();
    }

    @Test
    void ensureIsIdempotentWhenRowsAlreadyExist() {
        Routine routine = routine(6L, 10L, RoutineCategory.SKIN, RepeatType.DAILY, TODAY);
        when(routineRepository.findById(6L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(6L, TODAY, TODAY.plusDays(1)))
                .thenReturn(List.of(
                        DailyRoutine.createSnapshot(routine, TODAY, NOW),
                        DailyRoutine.createSnapshot(routine, TODAY.plusDays(1), NOW)
                ));

        int created = service.ensureMaterializedForRoutine(6L, TODAY.plusDays(1));

        assertThat(created).isZero();
        verify(dailyRoutineRepository, never()).saveAll(any());
    }

    @Test
    void existingGapIsBackfilled() {
        Routine routine = routine(7L, 10L, RoutineCategory.SKIN, RepeatType.DAILY, TODAY);
        when(routineRepository.findById(7L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(7L, TODAY, TODAY.plusDays(3)))
                .thenReturn(List.of(
                        DailyRoutine.createSnapshot(routine, TODAY, NOW),
                        DailyRoutine.createSnapshot(routine, TODAY.plusDays(2), NOW)
                ));

        int created = service.ensureMaterializedForRoutine(7L, TODAY.plusDays(3));

        assertThat(created).isEqualTo(2);
        assertThat(savedDates()).containsExactly(TODAY.plusDays(1), TODAY.plusDays(3));
    }

    @Test
    void userEnsureMaterializesRecurringRoutineThroughSixtyDayHorizon() {
        Routine routine = routine(8L, 30L, RoutineCategory.SKIN, RepeatType.DAILY, TODAY);
        LocalDate horizonEnd = TODAY.plusDays(DefaultDailyRoutineMaterializationService.MATERIALIZATION_HORIZON_DAYS);
        when(routineRepository.findByUserIdAndEffectiveFromLessThanEqualAndDeletedAtIsNullOrderByIdAsc(30L, horizonEnd))
                .thenReturn(List.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(8L, TODAY, horizonEnd))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForUser(30L);

        assertThat(created).isEqualTo(61);
        assertThat(savedDates()).first().isEqualTo(TODAY);
        assertThat(savedDates()).last().isEqualTo(horizonEnd);
    }

    @Test
    void softDeletedRoutineDoesNotMaterializeFutureRowsAfterDeletionDate() {
        Routine routine = routine(9L, 10L, RoutineCategory.SKIN, RepeatType.DAILY, TODAY);
        setField(routine, "deletedAt", NOW.plusSeconds(86_400));
        when(routineRepository.findById(9L)).thenReturn(Optional.of(routine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(9L, TODAY, TODAY.plusDays(1)))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForRoutine(9L, TODAY.plusDays(10));

        assertThat(created).isEqualTo(2);
        assertThat(savedDates()).containsExactly(TODAY, TODAY.plusDays(1));
    }

    @Test
    void userEnsureUsesOnlyRequestedUsersActiveRoutines() {
        Routine userRoutine = routine(10L, 40L, RoutineCategory.SKIN, RepeatType.ONCE, TODAY);
        Routine otherUserRoutine = routine(11L, 41L, RoutineCategory.SKIN, RepeatType.ONCE, TODAY);
        LocalDate horizonEnd = TODAY.plusDays(DefaultDailyRoutineMaterializationService.MATERIALIZATION_HORIZON_DAYS);
        when(routineRepository.findByUserIdAndEffectiveFromLessThanEqualAndDeletedAtIsNullOrderByIdAsc(40L, horizonEnd))
                .thenReturn(List.of(userRoutine));
        when(dailyRoutineRepository.findByRoutineIdAndServiceDateBetween(10L, TODAY, TODAY))
                .thenReturn(List.of());

        int created = service.ensureMaterializedForUser(40L);

        assertThat(created).isEqualTo(1);
        assertThat(savedDailyRoutines()).extracting(DailyRoutine::getUserId).containsExactly(40L);
        assertThat(savedDailyRoutines()).extracting(DailyRoutine::getRoutineId).doesNotContain(otherUserRoutine.getId());
    }

    private Routine routine(
            Long routineId,
            Long userId,
            RoutineCategory category,
            RepeatType repeatType,
            LocalDate effectiveFrom
    ) {
        Routine routine = Routine.create(
                userId,
                category,
                "routine-" + routineId,
                LocalTime.of(7, 0),
                LocalTime.of(8, 0),
                repeatType,
                "object-" + routineId,
                effectiveFrom,
                NOW
        );
        setField(routine, "id", routineId);
        return routine;
    }

    private List<LocalDate> savedDates() {
        return savedDailyRoutines().stream()
                .map(DailyRoutine::getServiceDate)
                .toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<DailyRoutine> savedDailyRoutines() {
        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(dailyRoutineRepository).saveAll(captor.capture());

        List<DailyRoutine> saved = new ArrayList<>();
        for (Object dailyRoutine : captor.getValue()) {
            saved.add((DailyRoutine) dailyRoutine);
        }
        return saved;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.of(2026, 8, 19);
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
