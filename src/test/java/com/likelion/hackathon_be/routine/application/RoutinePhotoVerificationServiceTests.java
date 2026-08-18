package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.PhotoMissionTemplate;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.AvatarStageChangedResponse;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import com.likelion.hackathon_be.routine.dto.SuccessSummaryResponse;
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.repository.PhotoMissionTemplateRepository;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalysis;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzerException;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationInput;
import com.likelion.hackathon_be.routine.verification.application.StoredVerificationPhoto;
import com.likelion.hackathon_be.routine.verification.application.UnavailablePhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.VerificationPhotoStorage;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.story.application.StoryProgressionResult;
import com.likelion.hackathon_be.story.application.StoryProgressionService;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutinePhotoVerificationServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 8, 19);
    private static final Long MISSION_TEMPLATE_ID = 501L;

    private UserRepository userRepository;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private RoutinePointClaimRepository pointClaimRepository;
    private PhotoMissionTemplateRepository photoMissionTemplateRepository;
    private PhotoVerificationAnalyzer analyzer;
    private FakePhotoStorage photoStorage;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        pointClaimRepository = mock(RoutinePointClaimRepository.class);
        photoMissionTemplateRepository = mock(PhotoMissionTemplateRepository.class);
        analyzer = mock(PhotoVerificationAnalyzer.class);
        photoStorage = new FakePhotoStorage();

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(photoMissionTemplateRepository.findById(MISSION_TEMPLATE_ID))
                .thenReturn(Optional.of(photoMissionTemplate(MISSION_TEMPLATE_ID, "thumbs_up")));
        when(verificationRepository.saveAndFlush(any(RoutineVerification.class))).thenAnswer(invocation -> {
            RoutineVerification verification = invocation.getArgument(0);
            setField(verification, "id", 900L);
            return verification;
        });
        when(dailySuccessRecordRepository.saveAndFlush(any(DailySuccessRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analyzer.analyze(any(PhotoVerificationInput.class))).thenReturn(PhotoVerificationAnalysis.success());
    }

    @Test
    void photoSuccessCreatesPhotoVerificationAndTenPointProjection() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());

        ArgumentCaptor<RoutineVerification> captor = ArgumentCaptor.forClass(RoutineVerification.class);
        verify(verificationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getVerificationType()).isEqualTo(VerificationType.PHOTO);
        assertThat(response.verification().type()).isEqualTo("PHOTO");
        assertThat(response.pointClaim().autoAwarded()).isFalse();
        assertThat(response.pointClaim().claimable()).isTrue();
        assertThat(response.pointClaim().rewardPoints()).isEqualTo(10);
        verify(pointClaimRepository, never()).save(any(RoutinePointClaim.class));
    }

    @Test
    void analyzerReceivesInMemoryPhotoObjectAndGestureThenBuffersAreZeroized() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        AtomicReference<byte[]> imageSeenByAnalyzer = new AtomicReference<>();
        when(analyzer.analyze(any(PhotoVerificationInput.class))).thenAnswer(invocation -> {
            PhotoVerificationInput input = invocation.getArgument(0);
            imageSeenByAnalyzer.set(input.image());
            return PhotoVerificationAnalysis.success();
        });

        service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());

        ArgumentCaptor<PhotoVerificationInput> captor = ArgumentCaptor.forClass(PhotoVerificationInput.class);
        verify(analyzer).analyze(captor.capture());
        assertThat(imageSeenByAnalyzer.get()).containsExactly(1, 2, 3);
        assertThat(captor.getValue().image()).containsOnly((byte) 0);
        assertThat(photoStorage.stored.image()).containsOnly((byte) 0);
        assertThat(captor.getValue().mediaType()).isEqualTo("image/jpeg");
        assertThat(captor.getValue().objectCode()).isEqualTo("object-1");
        assertThat(captor.getValue().gestureCode()).isEqualTo("thumbs_up");
    }

    @Test
    void pngPhotoPreservesMediaTypeThroughInMemoryContract() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        AtomicReference<String> mediaTypeSeenByAnalyzer = new AtomicReference<>();
        when(analyzer.analyze(any(PhotoVerificationInput.class))).thenAnswer(invocation -> {
            PhotoVerificationInput input = invocation.getArgument(0);
            mediaTypeSeenByAnalyzer.set(input.mediaType());
            return PhotoVerificationAnalysis.success();
        });

        service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, png());

        assertThat(mediaTypeSeenByAnalyzer).hasValue("image/png");
        assertThat(photoStorage.deleted).isTrue();
    }

    @Test
    void oversizedPhotoIsRejectedBeforeStorageCopiesTheRequest() throws Exception {
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getContentType()).thenReturn("image/jpeg");
        when(oversized.getSize()).thenReturn((long) PhotoVerificationInput.MAX_IMAGE_BYTES + 1);

        assertBusinessError(
                () -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, oversized),
                ErrorCode.VALIDATION_ERROR
        );

        verify(oversized, never()).getInputStream();
        verify(analyzer, never()).analyze(any());
        assertThat(photoStorage.stored).isNull();
    }

    @Test
    void otherUserDoesNotCallAnalyzer() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, OTHER_USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.DAILY_ROUTINE_NOT_FOUND);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void beforeStartDoesNotCallAnalyzer() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0, MISSION_TEMPLATE_ID);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 9, 59, 59)).verifyPhoto(1L, jpeg()),
                ErrorCode.ROUTINE_NOT_STARTED);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void afterEndDoesNotCallAnalyzer() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 11, 0, MISSION_TEMPLATE_ID);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 11, 1)).verifyPhoto(1L, jpeg()),
                ErrorCode.ROUTINE_WINDOW_CLOSED);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void capturedRequestTimeAllowsCompletionAfterWindowEnds() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 10, 0, 10, 0, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0, 30)).verifyPhoto(1L, jpeg());

        assertThat(response.verification().verifiedAt().toInstant()).isEqualTo(seoulInstant(SERVICE_DATE, 10, 0, 30));
    }

    @Test
    void missionMissingDoesNotCallAnalyzer() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, null);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(dailyRoutine));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.PHOTO_MISSION_NOT_PREPARED);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void alreadyVerifiedDoesNotCallAnalyzer() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        when(dailyRoutineRepository.findById(1L)).thenReturn(Optional.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineId(1L))
                .thenReturn(Optional.of(verification(1L, VerificationType.CHECK)));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.ALREADY_VERIFIED);
        verify(analyzer, never()).analyze(any());
    }

    @Test
    void objectFalseMapsToPhotoVerificationFailed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(analyzer.analyze(any())).thenReturn(new PhotoVerificationAnalysis(true, false, true));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.PHOTO_VERIFICATION_FAILED);
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void gestureFalseMapsToPhotoVerificationFailed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(analyzer.analyze(any())).thenReturn(new PhotoVerificationAnalysis(true, true, false));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.PHOTO_VERIFICATION_FAILED);
        verify(verificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void undecidableMapsToPhotoNotDecidable() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(analyzer.analyze(any())).thenReturn(new PhotoVerificationAnalysis(false, false, false));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.PHOTO_NOT_DECIDABLE);
    }

    @Test
    void analyzerExceptionMapsToPhotoAiUnavailable() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(analyzer.analyze(any())).thenThrow(new PhotoVerificationAnalyzerException("down"));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.PHOTO_AI_UNAVAILABLE);
    }

    @Test
    void unavailableAnalyzerMapsToPhotoAiUnavailable() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        DefaultRoutineVerificationService service = service(
                seoulInstant(SERVICE_DATE, 10, 0),
                new UnavailablePhotoVerificationAnalyzer(),
                photoStorage
        );

        assertBusinessError(() -> service.verifyPhoto(1L, jpeg()), ErrorCode.PHOTO_AI_UNAVAILABLE);
    }

    @Test
    void inMemoryPhotoDeletedAndZeroizedAfterSuccessFailureAndException() {
        DailyRoutine success = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(success, List.of(success));
        service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());
        assertThat(photoStorage.deleted).isTrue();
        assertThat(photoStorage.stored.image()).containsOnly((byte) 0);

        photoStorage = new FakePhotoStorage();
        DailyRoutine failure = dailyRoutine(2L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(failure, List.of(failure));
        when(analyzer.analyze(any())).thenReturn(new PhotoVerificationAnalysis(true, false, true));
        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(2L, jpeg()),
                ErrorCode.PHOTO_VERIFICATION_FAILED);
        assertThat(photoStorage.deleted).isTrue();
        assertThat(photoStorage.stored.image()).containsOnly((byte) 0);

        photoStorage = new FakePhotoStorage();
        DailyRoutine error = dailyRoutine(3L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(error, List.of(error));
        when(analyzer.analyze(any())).thenThrow(new PhotoVerificationAnalyzerException("down"));
        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(3L, jpeg()),
                ErrorCode.PHOTO_AI_UNAVAILABLE);
        assertThat(photoStorage.deleted).isTrue();
        assertThat(photoStorage.stored.image()).containsOnly((byte) 0);
    }

    @Test
    void analyzerRunsBeforeCompletionTransactionLock() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));

        service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());

        InOrder inOrder = inOrder(analyzer, userRepository);
        inOrder.verify(analyzer).analyze(any());
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
    }

    @Test
    void checkCompletedDuringPhotoAnalysisMakesFinalTransactionAlreadyVerified() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(dailyRoutine, List.of(dailyRoutine));
        when(verificationRepository.findByDailyRoutineId(1L))
                .thenReturn(Optional.empty(), Optional.of(verification(1L, VerificationType.CHECK)));

        assertBusinessError(() -> service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg()),
                ErrorCode.ALREADY_VERIFIED);
    }

    @Test
    void photoLastEligibleRoutineCreatesDailySuccess() {
        DailyRoutine first = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        DailyRoutine second = dailyRoutine(2L, USER_ID, RoutineCategory.DIET, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(second, List.of(first, second));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(verification(1L, VerificationType.CHECK)));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(2L, jpeg());

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().newlySucceeded()).isTrue();
        verify(dailySuccessRecordRepository).saveAndFlush(any(DailySuccessRecord.class));
    }

    @Test
    void unfinishedTodoDoesNotBlockPhotoDailySuccess() {
        DailyRoutine normal = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        DailyRoutine todo = dailyRoutine(2L, USER_ID, RoutineCategory.TO_DO, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(normal, List.of(normal, todo));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());

        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.SUCCESS);
        assertThat(response.dayResult().totalCount()).isEqualTo(1);
    }

    @Test
    void todoPhotoCreatesVerificationWithoutDailySuccessOrPointClaim() {
        DailyRoutine todo = dailyRoutine(1L, USER_ID, RoutineCategory.TO_DO, 0, 0, 23, 59, MISSION_TEMPLATE_ID);
        givenTargetAndLockedRows(todo, List.of(todo));

        RoutineVerificationResultResponse response = service(seoulInstant(SERVICE_DATE, 10, 0)).verifyPhoto(1L, jpeg());

        assertThat(response.verification().type()).isEqualTo("PHOTO");
        assertThat(response.dayResult().dayStatus()).isEqualTo(DayStatus.NO_ROUTINE);
        assertThat(response.pointClaim().claimable()).isFalse();
        verify(dailySuccessRecordRepository, never()).saveAndFlush(any());
    }

    private DefaultRoutineVerificationService service(Instant now) {
        return service(now, analyzer, photoStorage);
    }

    private DefaultRoutineVerificationService service(
            Instant now,
            PhotoVerificationAnalyzer analyzer,
            VerificationPhotoStorage storage
    ) {
        RoutineCompletionService completionService = new DefaultRoutineCompletionService(
                new FixedTimeProvider(now),
                userRepository,
                dailyRoutineRepository,
                verificationRepository,
                dailySuccessRecordRepository,
                pointClaimRepository,
                storyProgressionService()
        );
        return new DefaultRoutineVerificationService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(now),
                dailyRoutineRepository,
                verificationRepository,
                photoMissionTemplateRepository,
                analyzer,
                storage,
                completionService
        );
    }

    private StoryProgressionService storyProgressionService() {
        StoryProgressionResult result = new StoryProgressionResult(
                new SuccessSummaryResponse(0, 0, 0),
                List.of(),
                new AvatarStageChangedResponse(false, 1, 1)
        );
        StoryProgressionService service = mock(StoryProgressionService.class);
        when(service.progressAfterNewDailySuccess(any(Long.class), any(Instant.class)))
                .thenReturn(result);
        when(service.currentProgress(any(Long.class))).thenReturn(result);
        return service;
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
            int endMinute,
            Long missionTemplateId
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
        setField(dailyRoutine, "missionTemplateId", missionTemplateId);
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

    private PhotoMissionTemplate photoMissionTemplate(Long id, String gestureCode) {
        PhotoMissionTemplate template = newInstance(PhotoMissionTemplate.class);
        setField(template, "id", id);
        setField(template, "gestureCode", gestureCode);
        setField(template, "instructionTemplate", "take a photo");
        setField(template, "active", true);
        setField(template, "createdAt", seoulInstant(SERVICE_DATE, 9, 0));
        return template;
    }

    private User user(Long id) {
        User user = User.createGuest(seoulInstant(SERVICE_DATE, 9, 0));
        setField(user, "id", id);
        return user;
    }

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("photo", "user-file-name.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("photo", "user-file-name.png", "image/png", new byte[]{4, 5, 6});
    }

    private void assertBusinessError(ThrowingRunnable runnable, ErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private static Instant seoulInstant(LocalDate date, int hour, int minute) {
        return seoulInstant(date, hour, minute, 0);
    }

    private static Instant seoulInstant(LocalDate date, int hour, int minute, int second) {
        return date.atTime(hour, minute, second)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
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

    private static class FakePhotoStorage implements VerificationPhotoStorage {
        private StoredVerificationPhoto stored;
        private boolean deleted;

        @Override
        public StoredVerificationPhoto store(MultipartFile photo) {
            try {
                stored = new StoredVerificationPhoto(photo.getBytes(), photo.getContentType());
                deleted = false;
                return stored;
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void delete(StoredVerificationPhoto photo) {
            if (photo == stored) {
                photo.destroy();
                deleted = true;
            }
        }
    }
}
