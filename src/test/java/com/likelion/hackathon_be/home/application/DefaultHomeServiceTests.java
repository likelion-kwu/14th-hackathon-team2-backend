package com.likelion.hackathon_be.home.application;

import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.domain.AvatarAssetSource;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.home.dto.HomeResponse;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.story.application.StreakAnalysis;
import com.likelion.hackathon_be.story.application.StreakAnalysisService;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Constructor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultHomeServiceTests {
    private static final Long USER_ID = 10L;
    private static final Instant SERVER_NOW = Instant.parse("2026-08-19T01:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 8, 19);

    private TimeProvider timeProvider;
    private UserRepository userRepository;
    private AvatarRepository avatarRepository;
    private SpeechStyleProfileRepository speechStyleProfileRepository;
    private DailyRoutineMaterializationService materializationService;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private RoutinePointClaimRepository pointClaimRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private StreakAnalysisService streakAnalysisService;
    private UserStoryUnlockRepository userStoryUnlockRepository;
    private StoryEpisodeRepository storyEpisodeRepository;
    private UserItemRepository userItemRepository;
    private ItemRepository itemRepository;
    private DefaultHomeService service;

    @BeforeEach
    void setUp() throws Exception {
        timeProvider = mock(TimeProvider.class);
        userRepository = mock(UserRepository.class);
        avatarRepository = mock(AvatarRepository.class);
        speechStyleProfileRepository = mock(SpeechStyleProfileRepository.class);
        materializationService = mock(DailyRoutineMaterializationService.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        pointClaimRepository = mock(RoutinePointClaimRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        streakAnalysisService = mock(StreakAnalysisService.class);
        userStoryUnlockRepository = mock(UserStoryUnlockRepository.class);
        storyEpisodeRepository = mock(StoryEpisodeRepository.class);
        userItemRepository = mock(UserItemRepository.class);
        itemRepository = mock(ItemRepository.class);

        when(timeProvider.now()).thenReturn(SERVER_NOW);
        when(timeProvider.serviceZone()).thenReturn(SEOUL);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user("tester")));
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.of(avatar()));
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(true);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of());
        when(verificationRepository.findByDailyRoutineIdIn(anyCollection())).thenReturn(List.of());
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(0L);
        when(pointClaimRepository.sumAmountByUserIdAndClaimedAtBetween(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(0L);
        when(pointClaimRepository.countByUserIdAndServiceDate(USER_ID, SERVICE_DATE)).thenReturn(0L);
        when(dailySuccessRecordRepository.existsByUserIdAndServiceDate(USER_ID, SERVICE_DATE)).thenReturn(false);
        when(streakAnalysisService.analyze(USER_ID)).thenReturn(new StreakAnalysis(0, 0, 0));
        when(userStoryUnlockRepository.findMaximumAvatarStage(USER_ID)).thenReturn(Optional.empty());
        when(storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc()).thenReturn(List.of());
        when(userItemRepository.findAllByUserIdAndEquippedTrue(USER_ID)).thenReturn(List.of());
        when(itemRepository.findAllById(List.of())).thenReturn(List.of());

        CurrentUserProvider currentUserProvider = () -> new CurrentUser(USER_ID);
        service = new DefaultHomeService(
                currentUserProvider,
                timeProvider,
                userRepository,
                avatarRepository,
                speechStyleProfileRepository,
                materializationService,
                dailyRoutineRepository,
                verificationRepository,
                pointClaimRepository,
                dailySuccessRecordRepository,
                streakAnalysisService,
                userStoryUnlockRepository,
                storyEpisodeRepository,
                userItemRepository,
                itemRepository
        );
    }

    @Test
    void rejectsIncompleteOnboardingWhenNicknameIsMissing() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));

        assertThatThrownBy(() -> service.getHome())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ONBOARDING_INCOMPLETE));
    }

    @Test
    void rejectsIncompleteOnboardingWhenAvatarIsMissing() {
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHome())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ONBOARDING_INCOMPLETE));
    }

    @Test
    void rejectsIncompleteOnboardingWhenSpeechProfileIsMissing() {
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getHome())
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ONBOARDING_INCOMPLETE));
    }

    @Test
    void returnsAggregatedHomeAndUsesCapturedServerNowForDateAndMonth() throws Exception {
        DailyRoutine completed = dailyRoutine(1L, 101L, RoutineCategory.SKIN, 8, 0, 9, 0);
        DailyRoutine available = dailyRoutine(2L, 102L, RoutineCategory.DIET, 10, 0, 11, 0);
        DailyRoutine failed = dailyRoutine(3L, 103L, RoutineCategory.HEALTH_FIT, 7, 0, 8, 0);
        DailyRoutine todo = dailyRoutine(4L, 104L, RoutineCategory.TO_DO, 12, 0, 13, 0);
        RoutineVerification photo = RoutineVerification.create(1L, VerificationType.PHOTO, SERVER_NOW);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of(completed, available, failed, todo));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(photo));
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(230L);
        when(pointClaimRepository.sumAmountByUserIdAndClaimedAtBetween(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(70L);
        when(pointClaimRepository.countByUserIdAndServiceDate(USER_ID, SERVICE_DATE)).thenReturn(2L);
        when(streakAnalysisService.analyze(USER_ID)).thenReturn(new StreakAnalysis(9, 4, 9));
        when(userStoryUnlockRepository.findMaximumAvatarStage(USER_ID)).thenReturn(Optional.of(3));
        StoryEpisode ep1 = storyEpisode(1L, 1, 7, 2);
        StoryEpisode ep2 = storyEpisode(2L, 2, 14, 3);
        when(storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc()).thenReturn(List.of(ep1, ep2));
        when(userStoryUnlockRepository.findEpisodeIdsByUserIdAndEpisodeIdIn(USER_ID, java.util.Set.of(1L, 2L)))
                .thenReturn(List.of(1L));
        UserItem equipped = userItem(501L);
        Item item = item(501L, "HAT", "items/hat.png");
        when(userItemRepository.findAllByUserIdAndEquippedTrue(USER_ID)).thenReturn(List.of(equipped));
        when(itemRepository.findAllById(List.of(501L))).thenReturn(List.of(item));

        HomeResponse response = service.getHome();

        assertThat(response.serviceDate()).isEqualTo(SERVICE_DATE);
        assertThat(response.serverNow().toInstant()).isEqualTo(SERVER_NOW);
        assertThat(response.avatar().growthTrack()).isEqualTo("SKIN");
        assertThat(response.avatar().stage()).isEqualTo(3);
        assertThat(response.avatar().imageEndpoint()).isEqualTo("/api/v1/avatars/me/image");
        assertThat(response.avatar().equippedItems()).hasSize(1);
        assertThat(response.avatar().equippedItems().get(0).assetKey()).isEqualTo("items/hat.png");
        assertThat(response.progress().completedCount()).isEqualTo(1);
        assertThat(response.progress().totalCount()).isEqualTo(3);
        assertThat(response.progress().percentage()).isEqualTo(33);
        assertThat(response.progress().dayStatus()).isEqualTo("FAILED");
        assertThat(response.points().totalEarned()).isEqualTo(230);
        assertThat(response.points().currentMonthEarned()).isEqualTo(70);
        assertThat(response.points().todayClaimedCount()).isEqualTo(2);
        assertThat(response.points().todayClaimLimit()).isEqualTo(3);
        assertThat(response.success().totalSuccessDays()).isEqualTo(9);
        assertThat(response.success().currentStreakDays()).isEqualTo(4);
        assertThat(response.success().maxAchievedStreakDays()).isEqualTo(9);
        assertThat(response.unlockProgress().nextItemMilestonePoints()).isEqualTo(300);
        assertThat(response.unlockProgress().nextStoryEpisodeNumber()).isEqualTo(2);
        assertThat(response.unlockProgress().nextStoryRequiredStreakDays()).isEqualTo(14);
        assertThat(response.routines()).hasSize(4);
        assertThat(response.routines()).extracting("dailyRoutineId").containsExactly(1L, 2L, 3L, 4L);
        assertThat(response.routines().get(0).status()).isEqualTo("COMPLETED");
        assertThat(response.routines().get(0).verificationType()).isEqualTo("PHOTO");
        assertThat(response.routines().get(1).status()).isEqualTo("AVAILABLE");
        assertThat(response.routines().get(2).status()).isEqualTo("FAILED");
        assertThat(response.routines().get(3).verificationType()).isNull();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(pointClaimRepository).sumAmountByUserIdAndClaimedAtBetween(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                fromCaptor.capture(),
                toCaptor.capture()
        );
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-07-31T15:00:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
        verify(timeProvider, times(1)).now();
    }

    @Test
    void todoOnlyDateHasNoProgressButStillReturnsTodoRoutine() throws Exception {
        DailyRoutine todo = dailyRoutine(4L, 104L, RoutineCategory.TO_DO, 7, 0, 8, 0);
        RoutineVerification check = RoutineVerification.create(4L, VerificationType.CHECK, SERVER_NOW);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of(todo));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(4L))).thenReturn(List.of(check));

        HomeResponse response = service.getHome();

        assertThat(response.progress().completedCount()).isZero();
        assertThat(response.progress().totalCount()).isZero();
        assertThat(response.progress().percentage()).isZero();
        assertThat(response.progress().dayStatus()).isEqualTo("NO_ROUTINE");
        assertThat(response.routines()).hasSize(1);
        assertThat(response.routines().get(0).status()).isEqualTo("COMPLETED");
        assertThat(response.routines().get(0).verificationType()).isEqualTo("CHECK");
    }

    @Test
    void dailySuccessRecordOverridesDayStatusToSuccess() throws Exception {
        DailyRoutine failed = dailyRoutine(3L, 103L, RoutineCategory.HEALTH_FIT, 7, 0, 8, 0);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of(failed));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(3L))).thenReturn(List.of());
        when(dailySuccessRecordRepository.existsByUserIdAndServiceDate(USER_ID, SERVICE_DATE)).thenReturn(true);

        HomeResponse response = service.getHome();

        assertThat(response.progress().dayStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void upcomingRoutineStatusAndNoStoryRemaining() throws Exception {
        DailyRoutine upcoming = dailyRoutine(5L, 105L, RoutineCategory.SKIN, 15, 0, 16, 0);
        StoryEpisode ep5 = storyEpisode(5L, 5, 35, 3);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE))
                .thenReturn(List.of(upcoming));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(5L))).thenReturn(List.of());
        when(storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc()).thenReturn(List.of(ep5));
        when(userStoryUnlockRepository.findEpisodeIdsByUserIdAndEpisodeIdIn(USER_ID, java.util.Set.of(5L)))
                .thenReturn(List.of(5L));

        HomeResponse response = service.getHome();

        assertThat(response.progress().dayStatus()).isEqualTo("IN_PROGRESS");
        assertThat(response.routines().get(0).status()).isEqualTo("UPCOMING");
        assertThat(response.unlockProgress().nextStoryEpisodeNumber()).isNull();
        assertThat(response.unlockProgress().nextStoryRequiredStreakDays()).isNull();
    }

    @Test
    void materializesBeforeHomeReadAggregation() {
        service.getHome();

        InOrder inOrder = inOrder(materializationService, dailyRoutineRepository);
        inOrder.verify(materializationService).ensureMaterializedForUser(USER_ID);
        inOrder.verify(dailyRoutineRepository)
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, SERVICE_DATE);
    }

    private User user(String nickname) throws Exception {
        User user = instantiate(User.class);
        setField(user, "id", USER_ID);
        setField(user, "nickname", nickname);
        setField(user, "createdAt", SERVER_NOW);
        setField(user, "updatedAt", SERVER_NOW);
        return user;
    }

    private Avatar avatar() {
        return Avatar.create(USER_ID, AvatarGrowthTrack.SKIN, "defaults/skin", AvatarAssetSource.DEFAULT, SERVER_NOW);
    }

    private DailyRoutine dailyRoutine(
            Long id,
            Long routineId,
            RoutineCategory category,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) throws Exception {
        DailyRoutine dailyRoutine = instantiate(DailyRoutine.class);
        setField(dailyRoutine, "id", id);
        setField(dailyRoutine, "routineId", routineId);
        setField(dailyRoutine, "userId", USER_ID);
        setField(dailyRoutine, "serviceDate", SERVICE_DATE);
        setField(dailyRoutine, "categorySnapshot", category);
        setField(dailyRoutine, "contentSnapshot", "routine-" + id);
        setField(dailyRoutine, "startTimeSnapshot", LocalTime.of(startHour, startMinute));
        setField(dailyRoutine, "endTimeSnapshot", LocalTime.of(endHour, endMinute));
        setField(dailyRoutine, "verificationObjectSnapshot", "COSMETIC_CONTAINER");
        setField(dailyRoutine, "createdAt", SERVER_NOW);
        setField(dailyRoutine, "updatedAt", SERVER_NOW);
        return dailyRoutine;
    }

    private StoryEpisode storyEpisode(Long id, int episodeNumber, int requiredStreak, int avatarStage) throws Exception {
        StoryEpisode episode = instantiate(StoryEpisode.class);
        setField(episode, "id", id);
        setField(episode, "episodeNumber", episodeNumber);
        setField(episode, "requiredStreak", requiredStreak);
        setField(episode, "avatarStage", (short) avatarStage);
        setField(episode, "active", true);
        return episode;
    }

    private UserItem userItem(Long itemId) throws Exception {
        UserItem userItem = UserItem.create(USER_ID, itemId, SERVER_NOW);
        setField(userItem, "id", itemId + 1000);
        setField(userItem, "equipped", true);
        return userItem;
    }

    private Item item(Long id, String type, String assetKey) throws Exception {
        Item item = instantiate(Item.class);
        setField(item, "id", id);
        setField(item, "name", "item-" + id);
        setField(item, "itemType", type);
        setField(item, "assetKey", assetKey);
        setField(item, "active", true);
        setField(item, "createdAt", SERVER_NOW);
        return item;
    }

    private static <T> T instantiate(Class<T> type) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
