package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import com.likelion.hackathon_be.routine.repository.RoutineRepeatDayRepository;
import com.likelion.hackathon_be.routine.repository.RoutineRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutineServiceTests {

    private static final Long USER_ID = 1001L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");

    private CurrentUserProvider currentUserProvider;
    private UserRepository userRepository;
    private RoutineRepository routineRepository;
    private RoutineRepeatDayRepository repeatDayRepository;
    private RoutineCatalogService routineCatalogService;
    private RoutineScheduleCoordinator scheduleCoordinator;
    private DailyRoutineMaterializationService materializationService;
    private DefaultRoutineService service;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        userRepository = mock(UserRepository.class);
        routineRepository = mock(RoutineRepository.class);
        repeatDayRepository = mock(RoutineRepeatDayRepository.class);
        routineCatalogService = mock(RoutineCatalogService.class);
        scheduleCoordinator = mock(RoutineScheduleCoordinator.class);
        materializationService = mock(DailyRoutineMaterializationService.class);
        service = new DefaultRoutineService(
                currentUserProvider,
                userRepository,
                routineRepository,
                repeatDayRepository,
                new RoutinePolicyValidator(routineCatalogService),
                scheduleCoordinator,
                materializationService,
                new FixedTimeProvider()
        );

        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(User.createGuest(NOW)));
        when(routineCatalogService.supportsVerificationObject("CUP")).thenReturn(true);
        when(routineRepository.save(any(Routine.class))).thenAnswer(invocation -> {
            Routine routine = invocation.getArgument(0);
            if (routine.getId() == null) {
                setField(routine, "id", 101L);
            }
            return routine;
        });
    }

    @Test
    void createsRecurringRoutineForTodayBeforeStartTime() {
        RoutineResponse response = service.createRoutine(createDaily(LocalTime.of(13, 0)));

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.content()).isEqualTo("물 마시기");
        assertThat(response.effectiveFrom()).isEqualTo(TODAY);
        assertThat(response.appliedToCurrentServiceDate()).isTrue();
        verify(materializationService).ensureMaterializedForUser(USER_ID);
    }

    @Test
    void defersRecurringRoutineWhenTodaysStartTimeAlreadyPassed() {
        RoutineResponse response = service.createRoutine(createDaily(LocalTime.of(11, 0)));

        assertThat(response.effectiveFrom()).isEqualTo(TODAY.plusDays(1));
        assertThat(response.appliedToCurrentServiceDate()).isFalse();
    }

    @Test
    void usesNextSelectedDayAsEffectiveFrom() {
        RoutineResponse response = service.createRoutine(new CreateRoutineRequest(
                RoutineCategory.WELL_BEING,
                "물 마시기",
                null,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                RepeatType.DAYS_OF_WEEK,
                List.of(DayOfWeek.FRI),
                "CUP"
        ));

        assertThat(response.effectiveFrom()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(response.daysOfWeek()).containsExactly("FRI");
        assertThat(response.appliedToCurrentServiceDate()).isFalse();
    }

    @Test
    void rejectsTodayOnceAfterStartTimeOrServiceDateLock() {
        CreateRoutineRequest request = createOnce(TODAY, LocalTime.of(11, 0));

        assertThatThrownBy(() -> service.createRoutine(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ONCE_DATE));

        when(scheduleCoordinator.isServiceDateLocked(USER_ID, TODAY)).thenReturn(true);
        assertThatThrownBy(() -> service.createRoutine(createOnce(TODAY, LocalTime.of(13, 0))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SERVICE_DATE_LOCKED));
        verify(routineRepository, never()).save(any(Routine.class));
    }

    @Test
    void listsOnlyCurrentUsersActiveRoutinesWithRepeatDays() {
        Routine routine = routine(201L, RepeatType.DAYS_OF_WEEK, LocalTime.of(13, 0), TODAY);
        when(routineRepository.findByUserIdAndDeletedAtIsNullOrderByIdAsc(USER_ID)).thenReturn(List.of(routine));
        when(repeatDayRepository.findByIdRoutineIdIn(List.of(201L))).thenReturn(List.of(
                RoutineRepeatDay.of(201L, DayOfWeek.MON),
                RoutineRepeatDay.of(201L, DayOfWeek.WED)
        ));

        List<RoutineResponse> responses = service.getRoutines();

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo(201L);
            assertThat(response.daysOfWeek()).containsExactly("MON", "WED");
            assertThat(response.appliedToCurrentServiceDate()).isTrue();
        });
    }

    @Test
    void hidesOtherUsersAndDeletedRoutinesAsNotFound() {
        when(routineRepository.findByIdAndUserIdAndDeletedAtIsNull(999L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRoutine(999L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ROUTINE_NOT_FOUND));
    }

    @Test
    void updatePreservesTodayWhenPreviousRoutineAlreadyStarted() {
        Routine routine = routine(301L, RepeatType.DAILY, LocalTime.of(10, 0), TODAY);
        when(routineRepository.findByIdAndUserIdAndDeletedAtIsNull(301L, USER_ID))
                .thenReturn(Optional.of(routine));

        RoutineResponse response = service.updateRoutine(301L, updateDaily(LocalTime.of(13, 0)));

        assertThat(response.effectiveFrom()).isEqualTo(TODAY.plusDays(1));
        assertThat(response.appliedToCurrentServiceDate()).isFalse();
        verify(scheduleCoordinator).preserveHistory(301L, TODAY);
        verify(scheduleCoordinator).synchronizeUpdatedRoutine(301L, USER_ID, TODAY.plusDays(1));
    }

    @Test
    void updateReplacesRepeatDaysAndAppliesBeforeStartTime() {
        Routine routine = routine(302L, RepeatType.DAILY, LocalTime.of(13, 0), TODAY);
        when(routineRepository.findByIdAndUserIdAndDeletedAtIsNull(302L, USER_ID))
                .thenReturn(Optional.of(routine));
        UpdateRoutineRequest request = new UpdateRoutineRequest(
                RoutineCategory.WELL_BEING,
                "수정 루틴",
                null,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                RepeatType.DAYS_OF_WEEK,
                List.of(DayOfWeek.WED),
                "CUP"
        );

        RoutineResponse response = service.updateRoutine(302L, request);

        assertThat(response.effectiveFrom()).isEqualTo(TODAY);
        assertThat(response.daysOfWeek()).containsExactly("WED");
        verify(repeatDayRepository).deleteAllByRoutineId(302L);
        verify(repeatDayRepository).saveAll(any());
        verify(scheduleCoordinator).synchronizeUpdatedRoutine(302L, USER_ID, TODAY);
    }

    @Test
    void deleteSoftDeletesButPreservesAlreadyStartedToday() {
        Routine routine = routine(401L, RepeatType.DAILY, LocalTime.of(10, 0), TODAY);
        when(routineRepository.findByIdAndUserIdAndDeletedAtIsNull(401L, USER_ID))
                .thenReturn(Optional.of(routine));

        service.deleteRoutine(401L);

        assertThat(routine.getDeletedAt()).isEqualTo(NOW);
        verify(scheduleCoordinator).preserveHistory(401L, TODAY);
        verify(scheduleCoordinator).synchronizeDeletedRoutine(401L, USER_ID, TODAY.plusDays(1));
    }

    private CreateRoutineRequest createDaily(LocalTime startTime) {
        return new CreateRoutineRequest(
                RoutineCategory.WELL_BEING,
                "  물 마시기  ",
                null,
                startTime,
                startTime.plusHours(1),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        );
    }

    private CreateRoutineRequest createOnce(LocalDate scheduledDate, LocalTime startTime) {
        return new CreateRoutineRequest(
                RoutineCategory.TO_DO,
                "영양제 챙기기",
                scheduledDate,
                startTime,
                startTime.plusHours(1),
                RepeatType.ONCE,
                List.of(),
                "CUP"
        );
    }

    private UpdateRoutineRequest updateDaily(LocalTime startTime) {
        return new UpdateRoutineRequest(
                RoutineCategory.WELL_BEING,
                "수정 루틴",
                null,
                startTime,
                startTime.plusHours(1),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        );
    }

    private Routine routine(Long id, RepeatType repeatType, LocalTime startTime, LocalDate effectiveFrom) {
        Routine routine = Routine.create(
                USER_ID,
                RoutineCategory.WELL_BEING,
                "기존 루틴",
                startTime,
                startTime.plusHours(1),
                repeatType,
                "CUP",
                effectiveFrom,
                NOW.minusSeconds(3600)
        );
        setField(routine, "id", id);
        return routine;
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

    private record FixedTimeProvider() implements TimeProvider {

        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public LocalDate todayServiceDate() {
            return TODAY;
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
