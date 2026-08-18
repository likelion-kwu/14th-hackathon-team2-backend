package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.dto.AvatarStageChangedResponse;
import com.likelion.hackathon_be.routine.dto.SuccessSummaryResponse;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import com.likelion.hackathon_be.story.dto.StoryEpisodeResponse;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryServiceTests {

    private static final Long USER_ID = 10L;
    private static final Instant UNLOCKED_AT = Instant.parse("2026-07-20T14:10:00Z");

    private StoryProgressionService storyProgressionService;
    private StoryEpisodeRepository storyEpisodeRepository;
    private UserStoryUnlockRepository userStoryUnlockRepository;
    private DefaultStoryService service;

    @BeforeEach
    void setUp() {
        storyProgressionService = mock(StoryProgressionService.class);
        storyEpisodeRepository = mock(StoryEpisodeRepository.class);
        userStoryUnlockRepository = mock(UserStoryUnlockRepository.class);
        service = new DefaultStoryService(
                () -> new CurrentUser(USER_ID),
                storyProgressionService,
                storyEpisodeRepository,
                userStoryUnlockRepository,
                new FixedTimeProvider()
        );
    }

    @Test
    void combinesPartAProgressWithPermanentUnlockRecordsInEpisodeOrder() {
        StoryEpisode first = episode(1L, 1, 10, 2);
        StoryEpisode second = episode(2L, 2, 20, 3);
        UserStoryUnlock firstUnlock = UserStoryUnlock.create(USER_ID, 1L, UNLOCKED_AT);
        when(storyProgressionService.currentProgress(USER_ID)).thenReturn(progress(4, 10, 2));
        when(storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc())
                .thenReturn(List.of(first, second));
        when(userStoryUnlockRepository.findByUserId(USER_ID)).thenReturn(List.of(firstUnlock));

        StoryProgressResponse response = service.getStories();

        assertThat(response.currentStreakDays()).isEqualTo(4);
        assertThat(response.maxAchievedStreakDays()).isEqualTo(10);
        assertThat(response.avatarStage()).isEqualTo(2);
        assertThat(response.episodes()).containsExactly(
                new StoryEpisodeResponse(
                        1,
                        10,
                        true,
                        UNLOCKED_AT.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()
                ),
                new StoryEpisodeResponse(2, 20, false, null)
        );
        verify(storyProgressionService).currentProgress(USER_ID);
        verify(userStoryUnlockRepository, never()).save(any());
    }

    @Test
    void noUnlockUsesStageOneFromPartAProgress() {
        StoryEpisode first = episode(1L, 1, 10, 2);
        when(storyProgressionService.currentProgress(USER_ID)).thenReturn(progress(0, 0, 1));
        when(storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc()).thenReturn(List.of(first));
        when(userStoryUnlockRepository.findByUserId(USER_ID)).thenReturn(List.of());

        StoryProgressResponse response = service.getStories();

        assertThat(response.avatarStage()).isEqualTo(1);
        assertThat(response.episodes().get(0).unlocked()).isFalse();
        assertThat(response.episodes().get(0).unlockedAt()).isNull();
    }

    @Test
    void responseContractDoesNotContainFrontendStoryContentFields() {
        assertThat(Arrays.stream(StoryEpisodeResponse.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("episodeNumber", "requiredStreakDays", "unlocked", "unlockedAt");
    }

    private StoryProgressionResult progress(int currentStreak, int maxStreak, int avatarStage) {
        return new StoryProgressionResult(
                new SuccessSummaryResponse(maxStreak, currentStreak, maxStreak),
                List.of(),
                new AvatarStageChangedResponse(false, avatarStage, avatarStage)
        );
    }

    private StoryEpisode episode(Long id, int number, int requiredStreak, int avatarStage) {
        StoryEpisode episode = newInstance(StoryEpisode.class);
        setField(episode, "id", id);
        setField(episode, "episodeNumber", number);
        setField(episode, "requiredStreak", requiredStreak);
        setField(episode, "avatarStage", (short) avatarStage);
        setField(episode, "active", true);
        return episode;
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

    private static class FixedTimeProvider implements TimeProvider {

        @Override
        public Instant now() {
            return UNLOCKED_AT;
        }

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
