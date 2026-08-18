package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DailyRoutineListResponse;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import java.lang.reflect.Constructor;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyRoutineServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-08-19T01:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private DailyRoutineMaterializationService materializationService;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private RoutinePointClaimRepository pointClaimRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private DefaultDailyRoutineService service;

    @BeforeEach
    void setUp() {
        materializationService = mock(DailyRoutineMaterializationService.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        pointClaimRepository = mock(RoutinePointClaimRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        service = new DefaultDailyRoutineService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(NOW),
                materializationService,
                dailyRoutineRepository,
                verificationRepository,
                pointClaimRepository,
                dailySuccessRecordRepository
        );
    }

    @Test
    void returnsOnlyAuthenticatedUsersDailyRoutinesForRequestedDate() {
        DailyRoutine userRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine otherUserRoutine = dailyRoutine(2L, 102L, OTHER_USER_ID, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        givenDailyRoutines(TODAY, List.of(userRoutine));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines()).hasSize(1);
        assertThat(response.routines().get(0).id()).isEqualTo(1L);
        assertThat(response.routines()).extracting("id").doesNotContain(otherUserRoutine.getId());
        verify(dailyRoutineRepository)
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, TODAY);
    }

    @Test
    void omittedDateUsesTodayFromTimeProviderAndMaterializesBeforeQuery() {
        givenDailyRoutines(TODAY, List.of());

        service.getDailyRoutines(null);

        InOrder inOrder = inOrder(materializationService, dailyRoutineRepository);
        inOrder.verify(materializationService).ensureMaterializedForUser(USER_ID);
        inOrder.verify(dailyRoutineRepository)
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, TODAY);
    }

    @Test
    void verificationProjectsCompletedStatusAndVerifiedAt() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        RoutineVerification verification = verification(1L, VerificationType.PHOTO, NOW.minusSeconds(60));
        givenDailyRoutines(TODAY, List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L))).thenReturn(List.of(verification));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines().get(0).status()).isEqualTo(DailyRoutineStatus.COMPLETED);
        assertThat(response.routines().get(0).verification().type()).isEqualTo("PHOTO");
        assertThat(response.routines().get(0).verification().verifiedAt().toInstant()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void derivesUpcomingAvailableAndFailedStatuses() {
        DailyRoutine failed = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine available = dailyRoutine(2L, 102L, USER_ID, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine upcoming = dailyRoutine(3L, 103L, USER_ID, RoutineCategory.SKIN, TODAY, 12, 0, 13, 0);
        givenDailyRoutines(TODAY, List.of(failed, available, upcoming));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines()).extracting("status")
                .containsExactly(DailyRoutineStatus.FAILED, DailyRoutineStatus.AVAILABLE, DailyRoutineStatus.UPCOMING);
    }

    @Test
    void endTime2359ProducesExclusiveMidnightOfNextDay() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 22, 0, 23, 59);
        givenDailyRoutines(TODAY, List.of(dailyRoutine));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines().get(0).actualEndAtExclusive())
                .isEqualTo(TODAY.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime());
    }

    @Test
    void progressRoundsCompletedEligibleRoutines() {
        DailyRoutine first = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine second = dailyRoutine(2L, 102L, USER_ID, RoutineCategory.DIET, TODAY, 10, 0, 11, 0);
        DailyRoutine third = dailyRoutine(3L, 103L, USER_ID, RoutineCategory.HEALTH_FIT, TODAY, 12, 0, 13, 0);
        givenDailyRoutines(TODAY, List.of(first, second, third));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(verification(1L, VerificationType.CHECK, NOW), verification(2L, VerificationType.PHOTO, NOW)));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.completedCount()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.percentage()).isEqualTo(67);
    }

    @Test
    void noEligibleRoutineReturnsNoRoutineProgress() {
        givenDailyRoutines(TODAY, List.of());

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.dayStatus()).isEqualTo(DayStatus.NO_ROUTINE);
        assertThat(response.completedCount()).isZero();
        assertThat(response.totalCount()).isZero();
        assertThat(response.percentage()).isZero();
    }

    @Test
    void todoOnlyIsListedButExcludedFromProgressAndPointClaim() {
        DailyRoutine todo = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.TO_DO, TODAY, 10, 0, 11, 0);
        givenDailyRoutines(TODAY, List.of(todo));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L)))
                .thenReturn(List.of(verification(1L, VerificationType.CHECK, NOW)));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines()).hasSize(1);
        assertThat(response.routines().get(0).status()).isEqualTo(DailyRoutineStatus.COMPLETED);
        assertThat(response.routines().get(0).pointClaim()).isNull();
        assertThat(response.dayStatus()).isEqualTo(DayStatus.NO_ROUTINE);
        assertThat(response.completedCount()).isZero();
        assertThat(response.totalCount()).isZero();
        assertThat(response.percentage()).isZero();
    }

    @Test
    void todoCompletionDoesNotAffectMixedProgress() {
        DailyRoutine completed = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        DailyRoutine pending = dailyRoutine(2L, 102L, USER_ID, RoutineCategory.DIET, TODAY, 12, 0, 13, 0);
        DailyRoutine todo = dailyRoutine(3L, 103L, USER_ID, RoutineCategory.TO_DO, TODAY, 10, 0, 11, 0);
        givenDailyRoutines(TODAY, List.of(completed, todo, pending));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 3L, 2L)))
                .thenReturn(List.of(verification(1L, VerificationType.CHECK, NOW), verification(3L, VerificationType.CHECK, NOW)));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.completedCount()).isEqualTo(1);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.percentage()).isEqualTo(50);
    }

    @Test
    void dailySuccessRecordMakesDayStatusSuccess() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        givenDailyRoutines(TODAY, List.of(dailyRoutine));
        when(dailySuccessRecordRepository.existsByUserIdAndServiceDate(USER_ID, TODAY)).thenReturn(true);

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.dayStatus()).isEqualTo(DayStatus.SUCCESS);
    }

    @Test
    void endedUnverifiedEligibleRoutineMakesDayStatusFailed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 8, 0, 9, 0);
        givenDailyRoutines(TODAY, List.of(dailyRoutine));

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.dayStatus()).isEqualTo(DayStatus.FAILED);
    }

    @Test
    void pointClaimProjectsRewardClaimedAndClaimable() {
        DailyRoutine photoRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, TODAY, 10, 0, 11, 0);
        DailyRoutine checkRoutine = dailyRoutine(2L, 102L, USER_ID, RoutineCategory.DIET, TODAY, 10, 0, 11, 0);
        givenDailyRoutines(TODAY, List.of(photoRoutine, checkRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(verification(1L, VerificationType.PHOTO, NOW), verification(2L, VerificationType.CHECK, NOW)));
        when(pointClaimRepository.findByDailyRoutineIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(pointClaim(2L, USER_ID, (short) 5, NOW)));
        when(pointClaimRepository.countByUserIdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(USER_ID, dayStart(TODAY), dayStart(TODAY.plusDays(1))))
                .thenReturn(2L);

        DailyRoutineListResponse response = service.getDailyRoutines(TODAY);

        assertThat(response.routines().get(0).pointClaim().rewardPoints()).isEqualTo(10);
        assertThat(response.routines().get(0).pointClaim().claimed()).isFalse();
        assertThat(response.routines().get(0).pointClaim().claimable()).isTrue();
        assertThat(response.routines().get(1).pointClaim().rewardPoints()).isEqualTo(5);
        assertThat(response.routines().get(1).pointClaim().claimed()).isTrue();
        assertThat(response.routines().get(1).pointClaim().claimable()).isFalse();
    }

    @Test
    void pointClaimIsNotClaimableForNonTodayServiceDate() {
        LocalDate yesterday = TODAY.minusDays(1);
        DailyRoutine dailyRoutine = dailyRoutine(1L, 101L, USER_ID, RoutineCategory.SKIN, yesterday, 10, 0, 11, 0);
        givenDailyRoutines(yesterday, List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L)))
                .thenReturn(List.of(verification(1L, VerificationType.PHOTO, NOW)));

        DailyRoutineListResponse response = service.getDailyRoutines(yesterday);

        assertThat(response.routines().get(0).pointClaim().claimable()).isFalse();
    }

    private void givenDailyRoutines(LocalDate serviceDate, List<DailyRoutine> dailyRoutines) {
        List<Long> ids = dailyRoutines.stream().map(DailyRoutine::getId).toList();
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, serviceDate))
                .thenReturn(dailyRoutines);
        when(verificationRepository.findByDailyRoutineIdIn(ids)).thenReturn(List.of());
        when(pointClaimRepository.findByDailyRoutineIdIn(ids)).thenReturn(List.of());
    }

    private DailyRoutine dailyRoutine(
            Long dailyRoutineId,
            Long routineId,
            Long userId,
            RoutineCategory category,
            LocalDate serviceDate,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        Routine routine = Routine.create(
                userId,
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

    private RoutineVerification verification(Long dailyRoutineId, VerificationType type, Instant verifiedAt) {
        RoutineVerification verification = newInstance(RoutineVerification.class);
        setField(verification, "dailyRoutineId", dailyRoutineId);
        setField(verification, "verificationType", type);
        setField(verification, "verifiedAt", verifiedAt);
        setField(verification, "createdAt", verifiedAt);
        return verification;
    }

    private RoutinePointClaim pointClaim(Long dailyRoutineId, Long userId, short amount, Instant claimedAt) {
        RoutinePointClaim pointClaim = newInstance(RoutinePointClaim.class);
        setField(pointClaim, "userId", userId);
        setField(pointClaim, "dailyRoutineId", dailyRoutineId);
        setField(pointClaim, "amount", amount);
        setField(pointClaim, "claimedAt", claimedAt);
        setField(pointClaim, "createdAt", claimedAt);
        return pointClaim;
    }

    private Instant dayStart(LocalDate date) {
        return date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
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
