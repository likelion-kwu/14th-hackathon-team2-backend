package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.AvatarStageChangedResponse;
import com.likelion.hackathon_be.routine.dto.StoryUnlockResponse;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryProgressionServiceTests {

    private static final Long USER_ID = 10L;
    private static final Instant NOW = Instant.parse("2026-08-19T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private DailyRoutineRepository dailyRoutineRepository;
    private DailySuccessRecordRepository dailySuccessRecordRepository;
    private RoutineVerificationRepository verificationRepository;

    @BeforeEach
    void setUp() {
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        dailySuccessRecordRepository = mock(DailySuccessRecordRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
    }

    @Test
    void oneSuccessDayHasOneCurrentAndMaxStreakWithoutStoryUnlock() {
        givenScheduledDates(daysAgo(0));
        givenSuccessDates(daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(1L);

        StreakAnalysis analysis = streakService(NOW).analyze(USER_ID);

        assertThat(analysis.totalSuccessDays()).isEqualTo(1);
        assertThat(analysis.currentStreakDays()).isEqualTo(1);
        assertThat(analysis.maxAchievedStreakDays()).isEqualTo(1);
    }

    @Test
    void failureScheduledDateResetsCurrentStreakButKeepsMax() {
        givenScheduledDates(daysAgo(3), daysAgo(2), daysAgo(1), daysAgo(0));
        givenSuccessDates(daysAgo(3), daysAgo(2), daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(3L);

        StreakAnalysis analysis = streakService(NOW).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isEqualTo(1);
        assertThat(analysis.maxAchievedStreakDays()).isEqualTo(2);
    }

    @Test
    void noRoutineDatesAreSkippedAndDoNotBreakStreak() {
        givenScheduledDates(daysAgo(3), daysAgo(0));
        givenSuccessDates(daysAgo(3), daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(2L);

        StreakAnalysis analysis = streakService(NOW).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isEqualTo(2);
        assertThat(analysis.maxAchievedStreakDays()).isEqualTo(2);
    }

    @Test
    void todoOnlyDatesAreNotScheduledByRepositoryAndDoNotAffectStreak() {
        givenScheduledDates(daysAgo(1), daysAgo(0));
        givenSuccessDates(daysAgo(1), daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(2L);

        StreakAnalysis analysis = streakService(NOW).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isEqualTo(2);
        verify(dailyRoutineRepository).findScheduledServiceDatesByUserIdThroughDateExcludingCategory(
                USER_ID,
                TODAY,
                RoutineCategory.TO_DO
        );
    }

    @Test
    void futureMaterializedDatesAreExcludedByThroughDate() {
        givenScheduledDates(daysAgo(1), daysAgo(0));
        givenSuccessDates(daysAgo(1), daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(2L);

        streakService(NOW).analyze(USER_ID);

        verify(dailyRoutineRepository).findScheduledServiceDatesByUserIdThroughDateExcludingCategory(
                USER_ID,
                TODAY,
                RoutineCategory.TO_DO
        );
    }

    @Test
    void todayInProgressDoesNotBreakExistingCurrentStreak() {
        givenScheduledDates(daysAgo(1), daysAgo(0));
        givenSuccessDates(daysAgo(1));
        DailyRoutine todayRoutine = dailyRoutine(1L, TODAY, RoutineCategory.SKIN, 10, 0, 11, 0);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, TODAY))
                .thenReturn(List.of(todayRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L))).thenReturn(List.of());
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(1L);

        StreakAnalysis analysis = streakService(instant(TODAY, 9, 0)).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isEqualTo(1);
        assertThat(analysis.maxAchievedStreakDays()).isEqualTo(1);
    }

    @Test
    void todayConfirmedFailureBreaksCurrentStreak() {
        givenScheduledDates(daysAgo(1), daysAgo(0));
        givenSuccessDates(daysAgo(1));
        DailyRoutine todayRoutine = dailyRoutine(1L, TODAY, RoutineCategory.SKIN, 10, 0, 11, 0);
        when(dailyRoutineRepository.findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(USER_ID, TODAY))
                .thenReturn(List.of(todayRoutine));
        when(verificationRepository.findByDailyRoutineIdIn(List.of(1L))).thenReturn(List.of());
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(1L);

        StreakAnalysis analysis = streakService(instant(TODAY, 11, 1)).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isZero();
        assertThat(analysis.maxAchievedStreakDays()).isEqualTo(1);
    }

    @Test
    void nonTodoPlusTodoDateUsesNonTodoSuccessOnly() {
        givenScheduledDates(daysAgo(0));
        givenSuccessDates(daysAgo(0));
        when(dailySuccessRecordRepository.countByUserId(USER_ID)).thenReturn(1L);

        StreakAnalysis analysis = streakService(NOW).analyze(USER_ID);

        assertThat(analysis.currentStreakDays()).isEqualTo(1);
    }

    @Test
    void sixDayMaxDoesNotUnlockStoryAndKeepsStageOne() {
        StoryProgressionResult result = storyService(new StreakAnalysis(6, 6, 6), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).isEmpty();
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 1, 1));
    }

    @Test
    void sevenDayMaxUnlocksEpisodeOneAndChangesStageFromOneToTwo() {
        StoryProgressionResult result = storyService(new StreakAnalysis(7, 7, 7), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.successSummary().totalSuccessDays()).isEqualTo(7);
        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(1, 7));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(true, 1, 2));
    }

    @Test
    void thirteenDayMaxKeepsOnlyEpisodeOneUnlocked() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(13, 13, 13), episodes(), List.of(ep1))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).isEmpty();
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 2, 2));
    }

    @Test
    void fourteenDayMaxUnlocksEpisodeTwoAndChangesStageFromTwoToThree() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(14, 14, 14), episodes(), List.of(ep1))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(2, 14));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(true, 2, 3));
    }

    @Test
    void twentyDayMaxKeepsOnlyEpisodeTwoUnlocked() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(2));
        UserStoryUnlock ep2 = UserStoryUnlock.create(USER_ID, 2L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(20, 20, 20), episodes(), List.of(ep1, ep2))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).isEmpty();
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
    }

    @Test
    void twentyOneDayMaxUnlocksEpisodeThreeWithoutChangingStageFromThree() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(2));
        UserStoryUnlock ep2 = UserStoryUnlock.create(USER_ID, 2L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(21, 21, 21), episodes(), List.of(ep1, ep2))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(3, 21));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
    }

    @Test
    void twentySevenAndTwentyEightBoundaryControlsEpisodeFour() {
        List<UserStoryUnlock> firstThree = List.of(
                UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(3)),
                UserStoryUnlock.create(USER_ID, 2L, NOW.minusSeconds(2)),
                UserStoryUnlock.create(USER_ID, 3L, NOW.minusSeconds(1))
        );

        StoryProgressionResult twentySeven = storyService(new StreakAnalysis(27, 27, 27), episodes(), firstThree)
                .progressAfterNewDailySuccess(USER_ID, NOW);
        StoryProgressionResult twentyEight = storyService(new StreakAnalysis(28, 28, 28), episodes(), firstThree)
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(twentySeven.unlockedStories()).isEmpty();
        assertThat(twentySeven.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
        assertThat(twentyEight.unlockedStories()).containsExactly(new StoryUnlockResponse(4, 28));
        assertThat(twentyEight.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
    }

    @Test
    void thirtyFourAndThirtyFiveBoundaryControlsEpisodeFive() {
        List<UserStoryUnlock> firstFour = List.of(
                UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(4)),
                UserStoryUnlock.create(USER_ID, 2L, NOW.minusSeconds(3)),
                UserStoryUnlock.create(USER_ID, 3L, NOW.minusSeconds(2)),
                UserStoryUnlock.create(USER_ID, 4L, NOW.minusSeconds(1))
        );

        StoryProgressionResult thirtyFour = storyService(new StreakAnalysis(34, 34, 34), episodes(), firstFour)
                .progressAfterNewDailySuccess(USER_ID, NOW);
        StoryProgressionResult thirtyFive = storyService(new StreakAnalysis(35, 35, 35), episodes(), firstFour)
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(thirtyFour.unlockedStories()).isEmpty();
        assertThat(thirtyFour.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
        assertThat(thirtyFive.unlockedStories()).containsExactly(new StoryUnlockResponse(5, 35));
        assertThat(thirtyFive.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
    }

    @Test
    void maxTwentyTwoUnlocksAllMissingEpisodesUpToEpisodeThree() {
        StoryProgressionResult result = storyService(new StreakAnalysis(22, 22, 22), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(
                new StoryUnlockResponse(1, 7),
                new StoryUnlockResponse(2, 14),
                new StoryUnlockResponse(3, 21)
        );
    }

    @Test
    void alreadyUnlockedEpisodesAreNotSavedAgain() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));
        UserStoryUnlockRepository unlockRepository = mock(UserStoryUnlockRepository.class);
        when(unlockRepository.findByUserId(USER_ID)).thenReturn(List.of(ep1));
        when(unlockRepository.save(any(UserStoryUnlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoryProgressionResult result = storyService(
                new StreakAnalysis(7, 7, 7),
                episodes(),
                unlockRepository
        ).progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).isEmpty();
        verify(unlockRepository, never()).save(any(UserStoryUnlock.class));
    }

    @Test
    void storyUnlockPersistsUserStoryUnlockRows() {
        UserStoryUnlockRepository unlockRepository = mock(UserStoryUnlockRepository.class);
        when(unlockRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(unlockRepository.save(any(UserStoryUnlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        storyService(new StreakAnalysis(7, 7, 7), episodes(), unlockRepository)
                .progressAfterNewDailySuccess(USER_ID, NOW);

        ArgumentCaptor<UserStoryUnlock> captor = ArgumentCaptor.forClass(UserStoryUnlock.class);
        verify(unlockRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getEpisodeId()).isEqualTo(1L);
        assertThat(captor.getValue().getUnlockedAt()).isEqualTo(NOW);
    }

    @Test
    void currentProgressReturnsEmptyStoriesAndUnchangedStage() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(7, 7, 7), episodes(), List.of(ep1))
                .currentProgress(USER_ID);

        assertThat(result.unlockedStories()).isEmpty();
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 2, 2));
    }

    private DefaultStreakAnalysisService streakService(Instant now) {
        return new DefaultStreakAnalysisService(
                new FixedTimeProvider(now),
                dailyRoutineRepository,
                dailySuccessRecordRepository,
                verificationRepository
        );
    }

    private DefaultStoryProgressionService storyService(StreakAnalysis analysis, List<StoryEpisode> episodes) {
        return storyService(analysis, episodes, List.of());
    }

    private DefaultStoryProgressionService storyService(
            StreakAnalysis analysis,
            List<StoryEpisode> episodes,
            List<UserStoryUnlock> unlocks
    ) {
        UserStoryUnlockRepository unlockRepository = mock(UserStoryUnlockRepository.class);
        when(unlockRepository.findByUserId(USER_ID)).thenReturn(unlocks);
        when(unlockRepository.save(any(UserStoryUnlock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return storyService(analysis, episodes, unlockRepository);
    }

    private DefaultStoryProgressionService storyService(
            StreakAnalysis analysis,
            List<StoryEpisode> episodes,
            UserStoryUnlockRepository unlockRepository
    ) {
        StoryEpisodeRepository episodeRepository = mock(StoryEpisodeRepository.class);
        when(episodeRepository.findByActiveTrueOrderByRequiredStreakAscEpisodeNumberAsc()).thenReturn(episodes);
        return new DefaultStoryProgressionService(
                userId -> analysis,
                episodeRepository,
                unlockRepository
        );
    }

    private void givenScheduledDates(LocalDate... serviceDates) {
        when(dailyRoutineRepository.findScheduledServiceDatesByUserIdThroughDateExcludingCategory(
                USER_ID,
                TODAY,
                RoutineCategory.TO_DO
        )).thenReturn(List.of(serviceDates));
    }

    private void givenSuccessDates(LocalDate... serviceDates) {
        when(dailySuccessRecordRepository.findServiceDatesByUserIdThroughDate(USER_ID, TODAY))
                .thenReturn(List.of(serviceDates));
    }

    private List<StoryEpisode> episodes() {
        return List.of(
                storyEpisode(1L, 1, 7, (short) 2),
                storyEpisode(2L, 2, 14, (short) 3),
                storyEpisode(3L, 3, 21, (short) 3),
                storyEpisode(4L, 4, 28, (short) 3),
                storyEpisode(5L, 5, 35, (short) 3)
        );
    }

    private StoryEpisode storyEpisode(Long id, int episodeNumber, int requiredStreak, short avatarStage) {
        StoryEpisode episode = newInstance(StoryEpisode.class);
        setField(episode, "id", id);
        setField(episode, "episodeNumber", episodeNumber);
        setField(episode, "requiredStreak", requiredStreak);
        setField(episode, "avatarStage", avatarStage);
        setField(episode, "active", true);
        return episode;
    }

    private DailyRoutine dailyRoutine(
            Long id,
            LocalDate serviceDate,
            RoutineCategory category,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute
    ) {
        DailyRoutine dailyRoutine = newInstance(DailyRoutine.class);
        setField(dailyRoutine, "id", id);
        setField(dailyRoutine, "userId", USER_ID);
        setField(dailyRoutine, "serviceDate", serviceDate);
        setField(dailyRoutine, "categorySnapshot", category);
        setField(dailyRoutine, "startTimeSnapshot", LocalTime.of(startHour, startMinute));
        setField(dailyRoutine, "endTimeSnapshot", LocalTime.of(endHour, endMinute));
        return dailyRoutine;
    }

    private LocalDate daysAgo(int days) {
        return TODAY.minusDays(days);
    }

    private Instant instant(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

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
