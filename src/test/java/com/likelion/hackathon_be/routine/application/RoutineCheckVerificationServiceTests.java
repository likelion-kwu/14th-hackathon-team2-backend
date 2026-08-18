package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.VerificationPhotoStorage;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutineCheckVerificationServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 8, 19);

    private UserRepository userRepository;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private RoutinePointClaimRepository pointClaimRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        pointClaimRepository = mock(RoutinePointClaimRepository.class);

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(verificationRepository.saveAndFlush(any(RoutineVerification.class))).thenAnswer(invocation -> {
            RoutineVerification verification = invocation.getArgument(0);
            setField(verification, "id", 900L);
            return verification;
        });
        when(dailySuccessRecordRepository.saveAndFlush(any(DailySuccessRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalCheckCreatesRoutineVerification() {
        Instant requestedAt = seoulInstant(SERVICE_DATE, 10, 0);
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(requestedAt).verifyCheck(1L);

        ArgumentCaptor<RoutineVerification> captor = ArgumentCaptor.forClass(RoutineVerification.class);
        verify(verificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDailyRoutineId()).isEqualTo(1L);
        assertThat(captor.getValue().getVerificationType()).isEqualTo(VerificationType.CHECK);
        assertThat(captor.getValue().getVerifiedAt()).isEqualTo(requestedAt);
        assertThat(response.verification().type()).isEqualTo("CHECK");
        assertThat(response.dailyRoutine().status().name()).isEqualTo("COMPLETED");
    }

    @Test
    void locksUserBeforeServiceDateDailyRoutinesAndSave() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        InOrder inOrder = inOrder(userRepository, dailyRoutineRepository, verificationRepository);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(dailyRoutineRepository).findById(1L);
        inOrder.verify(dailyRoutineRepository).findByUserIdAndServiceDateForUpdateOrderByIdAsc(USER_ID, SERVICE_DATE);
        inOrder.verify(verificationRepository).saveAndFlush(any(RoutineVerification.class));
    }

    @Test
    void otherUsersDailyRoutineReturnsNotFound() {
        DailyRoutine otherUsersRoutine = dailyRoutine(1L, OTHER_USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(otherUsersRoutine));
        when(dailyRoutineRepository.findByUserIdAndServiceDateForUpdateOrderByIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of());

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L),
                ErrorCode.DAILY_ROUTINE_NOT_FOUND);
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void beforeStartThrowsRoutineNotStartedWithoutSavingVerification() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 9, 59, 59)).verifyCheck(1L),
                ErrorCode.ROUTINE_NOT_STARTED);
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void exactStartTimeSucceeds() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.verification().type()).isEqualTo("CHECK");
    }

    @Test
    void withinEndMinuteSucceeds() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 11, 0, 30)).verifyCheck(1L);

        assertThat(response.verification().type()).isEqualTo("CHECK");
    }

    @Test
    void exactEndExclusiveThrowsWindowClosed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 11, 1)).verifyCheck(1L),
                ErrorCode.ROUTINE_WINDOW_CLOSED);
    }

    @Test
    void endTime2359ClosesAtNextMidnight() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 22, 0, 23, 59);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE.plusDays(1), 0, 0)).verifyCheck(1L),
                ErrorCode.ROUTINE_WINDOW_CLOSED);
    }

    @Test
    void alreadyCheckVerifiedThrowsAlreadyVerified() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineId(1L))
                .thenReturn(Optional.of(verification(1L, VerificationType.CHECK)));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L),
                ErrorCode.ALREADY_VERIFIED);
    }

    @Test
    void alreadyPhotoVerifiedThrowsAlreadyVerified() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineId(1L))
                .thenReturn(Optional.of(verification(1L, VerificationType.PHOTO)));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L),
                ErrorCode.ALREADY_VERIFIED);
    }

    @Test
    void uniqueViolationDuringSaveBecomesAlreadyVerified() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(verificationRepository.saveAndFlush(any(RoutineVerification.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L),
                ErrorCode.ALREADY_VERIFIED);
    }

    @Test
    void twoOfThreeCompletedDoesNotCreateDailySuccess() {
        DailyRoutine first = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        DailyRoutine second = dailyRoutine(2L, USER_ID, RoutineCategory.DIET, 10, 0, 11, 0);
        DailyRoutine third = dailyRoutine(3L, USER_ID, RoutineCategory.WELL_BEING, 10, 0, 11, 0);
        givenTargetAndLockedRows(second, List.of(first, second, third));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(verification(1L, VerificationType.CHECK)));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(2L);

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.IN_PROGRESS);
        assertThat(response.dayResult().completedCount()).isEqualTo(2);
        assertThat(response.dayResult().totalCount()).isEqualTo(3);
        verify(dailySuccessRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void lastEligibleCheckCreatesDailySuccess() {
        DailyRoutine first = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        DailyRoutine second = dailyRoutine(2L, USER_ID, RoutineCategory.DIET, 10, 0, 11, 0);
        DailyRoutine third = dailyRoutine(3L, USER_ID, RoutineCategory.WELL_BEING, 10, 0, 11, 0);
        givenTargetAndLockedRows(third, List.of(first, second, third));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        verification(1L, VerificationType.CHECK),
                        verification(2L, VerificationType.PHOTO)
                ));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(3L);

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().newlySucceeded()).isTrue();
        verify(dailySuccessRecordRepository).saveAndFlush(any(DailySuccessRecord.class));
    }

    @Test
    void existingDailySuccessDoesNotCreateDuplicate() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(dailySuccessRecordRepository.findByUserIdAndServiceDate(USER_ID, SERVICE_DATE))
                .thenReturn(Optional.of(DailySuccessRecord.create(USER_ID, SERVICE_DATE, seoulInstant(SERVICE_DATE, 10, 0))));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().newlySucceeded()).isFalse();
        verify(dailySuccessRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void singleEligibleRoutineCreatesDailySuccess() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().newlySucceeded()).isTrue();
        verify(dailySuccessRecordRepository).saveAndFlush(any(DailySuccessRecord.class));
    }

    @Test
    void todoOnlyCheckCreatesVerificationButNoDailySuccess() {
        DailyRoutine todo = dailyRoutine(1L, USER_ID, RoutineCategory.TO_DO, 10, 0, 11, 0);
        givenTargetAndLockedRows(todo, List.of(todo));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.verification().type()).isEqualTo("CHECK");
        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.NO_ROUTINE);
        assertThat(response.dayResult().totalCount()).isZero();
        assertThat(response.pointClaim().claimable()).isFalse();
        verify(dailySuccessRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void todoDoesNotBlockDailySuccessForEligibleRoutine() {
        DailyRoutine normal = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        DailyRoutine todo = dailyRoutine(2L, USER_ID, RoutineCategory.TO_DO, 10, 0, 11, 0);
        givenTargetAndLockedRows(normal, List.of(normal, todo));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().totalCount()).isEqualTo(1);
        verify(dailySuccessRecordRepository).saveAndFlush(any(DailySuccessRecord.class));
    }

    @Test
    void checkSuccessDoesNotCreatePointClaimAndProjectsFivePointReward() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyCheck(1L);

        assertThat(response.pointClaim().autoAwarded()).isFalse();
        assertThat(response.pointClaim().claimable()).isTrue();
        assertThat(response.pointClaim().rewardPoints()).isEqualTo(5);
        verify(pointClaimRepository, never()).save(any(RoutinePointClaim.class));
    }

    private DefaultRoutineVerificationService service(Instant now) {
        RoutineCompletionService completionService = new DefaultRoutineCompletionService(
                new FixedTimeProvider(now),
                userRepository,
                dailyRoutineRepository,
                verificationRepository,
                dailySuccessRecordRepository,
                pointClaimRepository
        );
        return new DefaultRoutineVerificationService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(now),
                dailyRoutineRepository,
                verificationRepository,
                mock(PhotoMissionTemplateRepository.class),
                mock(PhotoVerificationAnalyzer.class),
                mock(VerificationPhotoStorage.class),
                completionService
        );
    }

    private void givenTargetAndLockedRows(DailyRoutine target, List<DailyRoutine> lockedRows) {
        when(dailyRoutineRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(dailyRoutineRepository.findByUserIdAndServiceDateForUpdateOrderByIdAsc(USER_ID, target.getServiceDate()))
                .thenReturn(lockedRows);
        when(verificationRepository.findByDailyRoutineId(target.getId())).thenReturn(Optional.empty());
        List<Long> eligibleIds = lockedRows.stream()
                .filter(dailyRoutine -> dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO)
                .map(DailyRoutine::getId)
                .toList();
        when(verificationRepository.findByDailyRoutineIdIn(eligibleIds)).thenReturn(List.of());
        when(dailySuccessRecordRepository.findByUserIdAndServiceDate(USER_ID, target.getServiceDate()))
                .thenReturn(Optional.empty());
    }

    private DailyRoutine dailyRoutine(
            Long id,
            Long userId,
            RoutineCategory category,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        Routine routine = Routine.create(
                userId,
                category,
                "routine-" + id,
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                category == RoutineCategory.TO_DO ? RepeatType.ONCE : RepeatType.DAILY,
                "object-" + id,
                SERVICE_DATE,
                seoulInstant(SERVICE_DATE, 9, 0)
        );
        setField(routine, "id", id + 100);
        DailyRoutine dailyRoutine = DailyRoutine.createSnapshot(routine, SERVICE_DATE, seoulInstant(SERVICE_DATE, 9, 0));
        setField(dailyRoutine, "id", id);
        return dailyRoutine;
    }

    private RoutineVerification verification(Long dailyRoutineId, VerificationType type) {
        RoutineVerification verification = RoutineVerification.create(
                dailyRoutineId,
                type,
                seoulInstant(SERVICE_DATE, 10, 0)
        );
        setField(verification, "id", dailyRoutineId + 800);
        return verification;
    }

    private User user(Long id) {
        User user = User.createGuest(seoulInstant(SERVICE_DATE, 9, 0));
        setField(user, "id", id);
        return user;
    }

    private static Instant seoulInstant(LocalDate date, int hour, int minute) {
        return seoulInstant(date, hour, minute, 0);
    }

    private static Instant seoulInstant(LocalDate date, int hour, int minute, int second) {
        return date.atTime(hour, minute, second)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
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
