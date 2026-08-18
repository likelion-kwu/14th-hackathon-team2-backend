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
    void tenDayMaxUnlocksEpisodeOneAndChangesStageFromOneToTwo() {
        StoryProgressionResult result = storyService(new StreakAnalysis(10, 10, 10), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.successSummary().totalSuccessDays()).isEqualTo(10);
        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(1, 10));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(true, 1, 2));
    }

    @Test
    void twentyDayMaxUnlocksEpisodeTwoAndChangesStageFromTwoToThree() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(20, 20, 20), episodes(), List.of(ep1))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(2, 20));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(true, 2, 3));
    }

    @Test
    void thirtyDayMaxUnlocksEpisodeThreeWithoutChangingStageFromThree() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(2));
        UserStoryUnlock ep2 = UserStoryUnlock.create(USER_ID, 2L, NOW.minusSeconds(1));

        StoryProgressionResult result = storyService(new StreakAnalysis(30, 30, 30), episodes(), List.of(ep1, ep2))
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(new StoryUnlockResponse(3, 30));
        assertThat(result.avatarStageChanged()).isEqualTo(new AvatarStageChangedResponse(false, 3, 3));
    }

    @Test
    void fortyAndFiftyMilestonesUnlockEpisodesFourAndFive() {
        StoryProgressionResult forty = storyService(new StreakAnalysis(40, 40, 40), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);
        StoryProgressionResult fifty = storyService(new StreakAnalysis(50, 50, 50), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(forty.unlockedStories()).extracting(StoryUnlockResponse::episodeNumber)
                .contains(1, 2, 3, 4);
        assertThat(fifty.unlockedStories()).extracting(StoryUnlockResponse::episodeNumber)
                .contains(1, 2, 3, 4, 5);
    }

    @Test
    void maxThirtyOneUnlocksAllMissingEpisodesUpToEpisodeThree() {
        StoryProgressionResult result = storyService(new StreakAnalysis(31, 31, 31), episodes())
                .progressAfterNewDailySuccess(USER_ID, NOW);

        assertThat(result.unlockedStories()).containsExactly(
                new StoryUnlockResponse(1, 10),
                new StoryUnlockResponse(2, 20),
                new StoryUnlockResponse(3, 30)
        );
    }

    @Test
    void alreadyUnlockedEpisodesAreNotSavedAgain() {
        UserStoryUnlock ep1 = UserStoryUnlock.create(USER_ID, 1L, NOW.minusSeconds(1));
        UserStoryUnlockRepository unlockRepository = mock(UserStoryUnlockRepository.class);
        when(unlockRepository.findByUserId(USER_ID)).thenReturn(List.of(ep1));
        when(unlockRepository.save(any(UserStoryUnlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StoryProgressionResult result = storyService(
                new StreakAnalysis(10, 10, 10),
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

        storyService(new StreakAnalysis(10, 10, 10), episodes(), unlockRepository)
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

        StoryProgressionResult result = storyService(new StreakAnalysis(10, 10, 10), episodes(), List.of(ep1))
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
                storyEpisode(1L, 1, 10, (short) 2),
                storyEpisode(2L, 2, 20, (short) 3),
                storyEpisode(3L, 3, 30, (short) 3),
                storyEpisode(4L, 4, 40, (short) 3),
                storyEpisode(5L, 5, 50, (short) 3)
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
