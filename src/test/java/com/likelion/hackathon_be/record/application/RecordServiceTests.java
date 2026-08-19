package com.likelion.hackathon_be.record.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.record.dto.RecordResponse;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.story.application.StreakAnalysis;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordServiceTests {

    private static final Long USER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-08-19T01:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private DailyRoutineMaterializationService materializationService;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private DefaultRecordService service;

    @BeforeEach
    void setUp() {
        materializationService = mock(DailyRoutineMaterializationService.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        service = new DefaultRecordService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(NOW),
                materializationService,
                dailyRoutineRepository,
                verificationRepository,
                dailySuccessRecordRepository,
                userId -> new StreakAnalysis(12, 4, 10)
        );
    }

    @Test
    void validatesDateRange() {
        assertBusinessError(() -> service.getRecords(TODAY, TODAY.minusDays(1)), ErrorCode.VALIDATION_ERROR);
        assertBusinessError(() -> service.getRecords(TODAY.minusDays(31), TODAY), ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void allowsThirtyOneDayRange() {
        givenRange(TODAY.minusDays(30), TODAY, List.of());

        RecordResponse response = service.getRecords(TODAY.minusDays(30), TODAY);

        assertThat(response.days()).hasSize(31);
    }

    @Test
    void materializesBeforeBulkReadAndReturnsOnlyRequestedRangeForUser() {
        LocalDate fromDate = TODAY.minusDays(2);
        givenRange(fromDate, TODAY, List.of());

        service.getRecords(fromDate, TODAY);

        InOrder inOrder = inOrder(materializationService, dailyRoutineRepository);
        inOrder.verify(materializationService).ensureMaterializedForUser(USER_ID);
        inOrder.verify(dailyRoutineRepository)
                .findByUserIdAndServiceDateBetweenOrderByServiceDateDescStartTimeSnapshotAscIdAsc(USER_ID, fromDate, TODAY);
    }

    @Test
    void noRoutinesReturnsEmptySummaryAndNoRoutineDays() {
        givenRange(TODAY.minusDays(1), TODAY, List.of());

        RecordResponse response = service.getRecords(TODAY.minusDays(1), TODAY);

        assertThat(response.summary().scheduledRoutineCount()).isZero();
        assertThat(response.summary().completedRoutineCount()).isZero();
        assertThat(response.summary().completionRate()).isZero();
        assertThat(response.days()).extracting("dayStatus").containsExactly("NO_ROUTINE", "NO_ROUTINE");
        assertThat(response.summary().totalSuccessDays()).isEqualTo(12);
        assertThat(response.summary().currentStreakDays()).isEqualTo(4);
        assertThat(response.summary().maxAchievedStreakDays()).isEqualTo(10);
    }

    @Test
    void summaryCountsEligibleCompletionsAndRoundsCompletionRate() {
        DailyRoutine first = dailyRoutine(1L, 101L, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine second = dailyRoutine(2L, 102L, RoutineCategory.DIET, TODAY, 10, 0, 11, 0);
        DailyRoutine third = dailyRoutine(3L, 103L, RoutineCategory.HEALTH_FIT, TODAY, 12, 0, 13, 0);
        givenRange(TODAY, TODAY, List.of(first, second, third));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(verification(1L, VerificationType.PHOTO), verification(2L, VerificationType.CHECK)));

        RecordResponse response = service.getRecords(TODAY, TODAY);

        assertThat(response.summary().scheduledRoutineCount()).isEqualTo(3);
        assertThat(response.summary().completedRoutineCount()).isEqualTo(2);
        assertThat(response.summary().completionRate()).isEqualTo(67);
        assertThat(response.summary().photoVerificationCount()).isEqualTo(1);
        assertThat(response.summary().checkVerificationCount()).isEqualTo(1);
    }

    @Test
    void todoIsListedButExcludedFromProgressionAggregatesAndVerificationCounts() {
        DailyRoutine normal = dailyRoutine(1L, 101L, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine todo = dailyRoutine(2L, 102L, RoutineCategory.TO_DO, TODAY, 10, 0, 11, 0);
        givenRange(TODAY, TODAY, List.of(normal, todo));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(verification(2L, VerificationType.CHECK)));

        RecordResponse response = service.getRecords(TODAY, TODAY);

        assertThat(response.summary().scheduledRoutineCount()).isEqualTo(1);
        assertThat(response.summary().completedRoutineCount()).isZero();
        assertThat(response.summary().checkVerificationCount()).isZero();
        assertThat(response.days().get(0).totalCount()).isEqualTo(1);
        assertThat(response.days().get(0).completedCount()).isZero();
        assertThat(response.days().get(0).routines()).hasSize(2);
        assertThat(response.days().get(0).routines().get(1).status()).isEqualTo("COMPLETED");
        assertThat(response.days().get(0).routines().get(1).verificationType()).isEqualTo("CHECK");
    }

    @Test
    void todoOnlyDayIsNoRoutineButStillListsRoutine() {
        DailyRoutine todo = dailyRoutine(1L, 101L, RoutineCategory.TO_DO, TODAY, 10, 0, 11, 0);
        givenRange(TODAY, TODAY, List.of(todo));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L)))
                .thenReturn(List.of(verification(1L, VerificationType.PHOTO)));

        RecordResponse response = service.getRecords(TODAY, TODAY);

        assertThat(response.days().get(0).dayStatus()).isEqualTo("NO_ROUTINE");
        assertThat(response.days().get(0).totalCount()).isZero();
        assertThat(response.days().get(0).completedCount()).isZero();
        assertThat(response.days().get(0).routines()).hasSize(1);
        assertThat(response.days().get(0).routines().get(0).verificationType()).isEqualTo("PHOTO");
    }

    @Test
    void derivesDayStatusForSuccessFailedInProgressAndFuture() {
        LocalDate yesterday = TODAY.minusDays(1);
        LocalDate tomorrow = TODAY.plusDays(1);
        DailyRoutine failed = dailyRoutine(1L, 101L, RoutineCategory.SKIN, yesterday, 8, 0, 9, 0);
        DailyRoutine inProgress = dailyRoutine(2L, 102L, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine future = dailyRoutine(3L, 103L, RoutineCategory.SKIN, tomorrow, 10, 0, 11, 0);
        givenRange(yesterday, tomorrow, List.of(future, inProgress, failed));
        when(dailySuccessRecordRepository.findServiceDatesByUserIdAndServiceDateBetween(USER_ID, yesterday, tomorrow))
                .thenReturn(List.of(TODAY));

        RecordResponse response = service.getRecords(yesterday, tomorrow);

        assertThat(response.days()).extracting("serviceDate").containsExactly(tomorrow, TODAY, yesterday);
        assertThat(response.days()).extracting("dayStatus").containsExactly("IN_PROGRESS", "SUCCESS", "FAILED");
        assertThat(response.days().get(0).routines().get(0).status()).isEqualTo("UPCOMING");
        assertThat(response.days().get(1).routines().get(0).status()).isEqualTo("AVAILABLE");
        assertThat(response.days().get(2).routines().get(0).status()).isEqualTo("FAILED");
    }

    @Test
    void providesMonthlyCalendarInputsForEveryDisplayCase() {
        LocalDate successDay = TODAY.minusDays(4);
        LocalDate partialDay = TODAY.minusDays(3);
        LocalDate failedDay = TODAY.minusDays(2);
        LocalDate todoOnlyDay = TODAY.minusDays(1);
        LocalDate futureDay = TODAY.plusDays(1);

        DailyRoutine success = dailyRoutine(1L, 101L, RoutineCategory.SKIN, successDay, 8, 0, 9, 0);
        DailyRoutine partialCompleted = dailyRoutine(
                2L, 102L, RoutineCategory.DIET, partialDay, 8, 0, 9, 0
        );
        DailyRoutine partialMissed = dailyRoutine(
                3L, 103L, RoutineCategory.HEALTH_FIT, partialDay, 10, 0, 11, 0
        );
        DailyRoutine failed = dailyRoutine(4L, 104L, RoutineCategory.WELL_BEING, failedDay, 8, 0, 9, 0);
        DailyRoutine todo = dailyRoutine(5L, 105L, RoutineCategory.TO_DO, todoOnlyDay, 8, 0, 9, 0);
        DailyRoutine today = dailyRoutine(6L, 106L, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine future = dailyRoutine(7L, 107L, RoutineCategory.DIET, futureDay, 8, 0, 9, 0);
        List<DailyRoutine> dailyRoutines = List.of(
                future,
                today,
                todo,
                failed,
                partialCompleted,
                partialMissed,
                success
        );
        givenRange(successDay, futureDay, dailyRoutines);
        when(verificationRepository.findByDailyRoutineIdIn(List.of(7L, 6L, 5L, 4L, 2L, 3L, 1L)))
                .thenReturn(List.of(
                        verification(1L, VerificationType.PHOTO),
                        verification(2L, VerificationType.CHECK),
                        verification(5L, VerificationType.CHECK)
                ));
        when(dailySuccessRecordRepository.findServiceDatesByUserIdAndServiceDateBetween(
                USER_ID,
                successDay,
                futureDay
        )).thenReturn(List.of(successDay));

        RecordResponse response = service.getRecords(successDay, futureDay);

        assertThat(response.days()).extracting("serviceDate")
                .containsExactly(futureDay, TODAY, todoOnlyDay, failedDay, partialDay, successDay);
        assertThat(response.days()).extracting("dayStatus")
                .containsExactly("IN_PROGRESS", "IN_PROGRESS", "NO_ROUTINE", "FAILED", "FAILED", "SUCCESS");
        assertThat(response.days()).extracting("completedCount")
                .containsExactly(0, 0, 0, 0, 1, 1);
        assertThat(response.days()).extracting("totalCount")
                .containsExactly(1, 1, 0, 1, 2, 1);
        assertThat(response.days().stream().filter(day -> day.dayStatus().equals("SUCCESS"))).hasSize(1);
        assertThat(response.days().get(2).routines()).singleElement()
                .extracting("status", "verificationType")
                .containsExactly("COMPLETED", "CHECK");
    }

    @Test
    void todayWithNoCompletionBecomesFailedAfterAnEligibleRoutineWindowCloses() {
        DailyRoutine missed = dailyRoutine(1L, 101L, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine upcoming = dailyRoutine(2L, 102L, RoutineCategory.DIET, TODAY, 12, 0, 13, 0);
        givenRange(TODAY, TODAY, List.of(missed, upcoming));

        RecordResponse response = service.getRecords(TODAY, TODAY);

        assertThat(response.days()).singleElement().satisfies(day -> {
            assertThat(day.dayStatus()).isEqualTo("FAILED");
            assertThat(day.completedCount()).isZero();
            assertThat(day.totalCount()).isEqualTo(2);
        });
    }

    @Test
    void verifiedRoutineIsCompletedAndVerificationTypeIsProjected() {
        DailyRoutine routine = dailyRoutine(1L, 101L, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        givenRange(TODAY, TODAY, List.of(routine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L)))
                .thenReturn(List.of(verification(1L, VerificationType.PHOTO)));

        RecordResponse response = service.getRecords(TODAY, TODAY);

        assertThat(response.days().get(0).routines().get(0).status()).isEqualTo("COMPLETED");
        assertThat(response.days().get(0).routines().get(0).verificationType()).isEqualTo("PHOTO");
    }

    @Test
    void usesBulkVerificationAndDailySuccessQueriesWithoutPerRoutineCalls() {
        DailyRoutine first = dailyRoutine(1L, 101L, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine second = dailyRoutine(2L, 102L, RoutineCategory.DIET, TODAY, 10, 0, 11, 0);
        givenRange(TODAY, TODAY, List.of(first, second));

        service.getRecords(TODAY, TODAY);

        verify(verificationRepository).findByDailyRoutineIdIn(List.of(1L, 2L));
        verify(dailySuccessRecordRepository).findServiceDatesByUserIdAndServiceDateBetween(USER_ID, TODAY, TODAY);
    }

    private void givenRange(LocalDate fromDate, LocalDate toDate, List<DailyRoutine> dailyRoutines) {
        when(dailyRoutineRepository.findByUserIdAndServiceDateBetweenOrderByServiceDateDescStartTimeSnapshotAscIdAsc(
                USER_ID,
                fromDate,
                toDate
        )).thenReturn(dailyRoutines);
        when(verificationRepository.findByDailyRoutineIdIn(dailyRoutines.stream().map(DailyRoutine::getId).toList()))
                .thenReturn(List.of());
        when(dailySuccessRecordRepository.findServiceDatesByUserIdAndServiceDateBetween(USER_ID, fromDate, toDate))
                .thenReturn(List.of());
    }

    private DailyRoutine dailyRoutine(
            Long dailyRoutineId,
            Long routineId,
            RoutineCategory category,
            LocalDate serviceDate,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        Routine routine = Routine.create(
                USER_ID,
                category,
                "routine-" + routineId,
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                category == RoutineCategory.TO_DO ? RepeatType.ONCE : RepeatType.DAILY,
                "object-" + routineId,
                serviceDate,
                NOW
        );
        setField(routine, "id", routineId);
        DailyRoutine dailyRoutine = DailyRoutine.createSnapshot(routine, serviceDate, NOW);
        setField(dailyRoutine, "id", dailyRoutineId);
        return dailyRoutine;
    }

    private RoutineVerification verification(Long dailyRoutineId, VerificationType type) {
        RoutineVerification verification = RoutineVerification.create(dailyRoutineId, type, NOW);
        setField(verification, "id", dailyRoutineId + 800);
        return verification;
    }

    private void assertBusinessError(ThrowingRunnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.ofInstant(now, serviceZone());
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
