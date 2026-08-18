package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.routine.dto.SuccessSummaryResponse;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import com.likelion.hackathon_be.story.dto.StoryEpisodeResponse;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultStoryService implements StoryService {

    private final CurrentUserProvider currentUserProvider;
    private final StoryProgressionService storyProgressionService;
    private final StoryEpisodeRepository storyEpisodeRepository;
    private final UserStoryUnlockRepository userStoryUnlockRepository;
    private final TimeProvider timeProvider;

    public DefaultStoryService(
            CurrentUserProvider currentUserProvider,
            StoryProgressionService storyProgressionService,
            StoryEpisodeRepository storyEpisodeRepository,
            UserStoryUnlockRepository userStoryUnlockRepository,
            TimeProvider timeProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.storyProgressionService = storyProgressionService;
        this.storyEpisodeRepository = storyEpisodeRepository;
        this.userStoryUnlockRepository = userStoryUnlockRepository;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public StoryProgressResponse getStories() {
        Long userId = currentUserProvider.getCurrentUser().id();
        StoryProgressionResult progress = storyProgressionService.currentProgress(userId);
        List<StoryEpisode> episodes = storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc();
        Map<Long, UserStoryUnlock> unlockByEpisodeId = userStoryUnlockRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.toMap(UserStoryUnlock::getEpisodeId, Function.identity()));
        SuccessSummaryResponse successSummary = progress.successSummary();

        return new StoryProgressResponse(
                successSummary.currentStreakDays(),
                successSummary.maxAchievedStreakDays(),
                progress.avatarStageChanged().currentStage(),
                episodes.stream()
                        .map(episode -> toResponse(episode, unlockByEpisodeId.get(episode.getId())))
                        .toList()
        );
    }

    private StoryEpisodeResponse toResponse(
            StoryEpisode episode,
            UserStoryUnlock unlock
    ) {
        return new StoryEpisodeResponse(
                episode.getEpisodeNumber(),
                episode.getRequiredStreak(),
                unlock != null,
                unlock == null ? null : toOffsetDateTime(unlock)
        );
    }

    private OffsetDateTime toOffsetDateTime(UserStoryUnlock unlock) {
        return unlock.getUnlockedAt().atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
