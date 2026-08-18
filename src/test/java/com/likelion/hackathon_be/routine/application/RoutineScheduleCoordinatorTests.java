package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutineScheduleCoordinatorTests {

    private static final Long USER_ID = 1001L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");

    private DailyRoutineMaterializationService materializationService;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private DefaultRoutineScheduleCoordinator coordinator;

    @BeforeEach
    void setUp() {
        materializationService = mock(DailyRoutineMaterializationService.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        coordinator = new DefaultRoutineScheduleCoordinator(
                materializationService,
                dailyRoutineRepository,
                verificationRepository
        );
    }

    @Test
    void serviceDateIsLockedWhenAnySuccessfulVerificationExists() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, 10L, TODAY);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, TODAY))
                .thenReturn(List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L)))
                .thenReturn(List.of(verification(1L)));

        assertThat(coordinator.isServiceDateLocked(USER_ID, TODAY)).isTrue();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void updateKeepsLockedDateAndRebuildsOnlyMutableFutureRows() {
        DailyRoutine lockedToday = dailyRoutine(1L, 10L, TODAY);
        DailyRoutine mutableTomorrow = dailyRoutine(2L, 10L, TODAY.plusDays(1));
        DailyRoutine otherRoutineToday = dailyRoutine(3L, 20L, TODAY);
        when(dailyRoutineRepository
                .findByRoutineIdAndServiceDateGreaterThanEqualOrderByServiceDateAsc(10L, TODAY))
                .thenReturn(List.of(lockedToday, mutableTomorrow));
        when(dailyRoutineRepository.findByUserIdAndServiceDateBetween(USER_ID, TODAY, TODAY.plusDays(1)))
                .thenReturn(List.of(lockedToday, mutableTomorrow, otherRoutineToday));
        when(verificationRepository.findByDailyRoutineIdIn(anyCollection()))
                .thenReturn(List.of(verification(3L)));

        coordinator.synchronizeUpdatedRoutine(10L, USER_ID, TODAY);

        ArgumentCaptor<Iterable> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(dailyRoutineRepository).deleteAllInBatch(captor.capture());
        List<DailyRoutine> deleted = new ArrayList<>();
        captor.getValue().forEach(value -> deleted.add((DailyRoutine) value));
        assertThat(deleted).extracting(DailyRoutine::getId).containsExactly(2L);
        verify(materializationService).ensureMaterializedForUser(USER_ID);
    }

    @Test
    void preserveHistoryUsesExistingMaterializationService() {
        coordinator.preserveHistory(10L, TODAY);

        verify(materializationService).ensureMaterializedForRoutine(10L, TODAY);
    }

    private DailyRoutine dailyRoutine(Long id, Long routineId, LocalDate serviceDate) {
        Routine routine = Routine.create(
                USER_ID,
                RoutineCategory.SKIN,
                "루틴",
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                RepeatType.DAILY,
                "CUP",
                TODAY,
                NOW
        );
        setField(routine, "id", routineId);
        DailyRoutine dailyRoutine = DailyRoutine.createSnapshot(routine, serviceDate, NOW);
        setField(dailyRoutine, "id", id);
        return dailyRoutine;
    }

    private RoutineVerification verification(Long dailyRoutineId) {
        RoutineVerification verification = newInstance(RoutineVerification.class);
        setField(verification, "dailyRoutineId", dailyRoutineId);
        setField(verification, "verificationType", VerificationType.CHECK);
        setField(verification, "verifiedAt", NOW);
        setField(verification, "createdAt", NOW);
        return verification;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
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
}
