package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.routine.dto.AvatarStageChangedResponse;
import com.likelion.hackathon_be.routine.dto.StoryUnlockResponse;
import com.likelion.hackathon_be.routine.dto.SuccessSummaryResponse;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class DefaultStoryProgressionService implements StoryProgressionService {

    private static final int DEFAULT_AVATAR_STAGE = 1;

    private final StreakAnalysisService streakAnalysisService;
    private final StoryEpisodeRepository storyEpisodeRepository;
    private final UserStoryUnlockRepository userStoryUnlockRepository;

    public DefaultStoryProgressionService(
            StreakAnalysisService streakAnalysisService,
            StoryEpisodeRepository storyEpisodeRepository,
            UserStoryUnlockRepository userStoryUnlockRepository
    ) {
        this.streakAnalysisService = streakAnalysisService;
        this.storyEpisodeRepository = storyEpisodeRepository;
        this.userStoryUnlockRepository = userStoryUnlockRepository;
    }

    @Override
    public StoryProgressionResult progressAfterNewDailySuccess(Long userId, Instant unlockedAt) {
        StreakAnalysis streak = streakAnalysisService.analyze(userId);
        List<StoryEpisode> activeEpisodes = storyEpisodeRepository
                .findByActiveTrueOrderByRequiredStreakAscEpisodeNumberAsc();
        List<UserStoryUnlock> existingUnlocks = userStoryUnlockRepository.findByUserId(userId);
        Map<Long, StoryEpisode> activeEpisodeById = activeEpisodes.stream()
                .collect(Collectors.toMap(StoryEpisode::getId, Function.identity()));

        int previousStage = avatarStage(existingUnlocks, activeEpisodeById);
        Set<Long> unlockedEpisodeIds = existingUnlocks.stream()
                .map(UserStoryUnlock::getEpisodeId)
                .collect(Collectors.toCollection(HashSet::new));

        List<StoryEpisode> newEpisodes = activeEpisodes.stream()
                .filter(episode -> episode.getRequiredStreak() <= streak.maxAchievedStreakDays())
                .filter(episode -> !unlockedEpisodeIds.contains(episode.getId()))
                .toList();

        for (StoryEpisode episode : newEpisodes) {
            try {
                userStoryUnlockRepository.save(UserStoryUnlock.create(userId, episode.getId(), unlockedAt));
                unlockedEpisodeIds.add(episode.getId());
            } catch (DataIntegrityViolationException exception) {
                unlockedEpisodeIds.add(episode.getId());
            }
        }

        int currentStage = avatarStage(unlockedEpisodeIds, activeEpisodeById);
        return new StoryProgressionResult(
                toSuccessSummary(streak),
                newEpisodes.stream()
                        .sorted(Comparator.comparingInt(StoryEpisode::getEpisodeNumber))
                        .map(this::toStoryUnlockResponse)
                        .toList(),
                new AvatarStageChangedResponse(
                        previousStage != currentStage,
                        previousStage,
                        currentStage
                )
        );
    }

    @Override
    public StoryProgressionResult currentProgress(Long userId) {
        StreakAnalysis streak = streakAnalysisService.analyze(userId);
        List<StoryEpisode> activeEpisodes = storyEpisodeRepository
                .findByActiveTrueOrderByRequiredStreakAscEpisodeNumberAsc();
        List<UserStoryUnlock> existingUnlocks = userStoryUnlockRepository.findByUserId(userId);
        Map<Long, StoryEpisode> activeEpisodeById = activeEpisodes.stream()
                .collect(Collectors.toMap(StoryEpisode::getId, Function.identity()));
        int stage = avatarStage(existingUnlocks, activeEpisodeById);

        return new StoryProgressionResult(
                toSuccessSummary(streak),
                List.of(),
                new AvatarStageChangedResponse(false, stage, stage)
        );
    }

    private SuccessSummaryResponse toSuccessSummary(StreakAnalysis streak) {
        return new SuccessSummaryResponse(
                streak.totalSuccessDays(),
                streak.currentStreakDays(),
                streak.maxAchievedStreakDays()
        );
    }

    private StoryUnlockResponse toStoryUnlockResponse(StoryEpisode episode) {
        return new StoryUnlockResponse(
                episode.getEpisodeNumber(),
                episode.getRequiredStreak()
        );
    }

    private int avatarStage(List<UserStoryUnlock> unlocks, Map<Long, StoryEpisode> activeEpisodeById) {
        Set<Long> episodeIds = unlocks.stream()
                .map(UserStoryUnlock::getEpisodeId)
                .collect(Collectors.toSet());
        return avatarStage(episodeIds, activeEpisodeById);
    }

    private int avatarStage(Set<Long> episodeIds, Map<Long, StoryEpisode> activeEpisodeById) {
        return episodeIds.stream()
                .map(activeEpisodeById::get)
                .filter(java.util.Objects::nonNull)
                .mapToInt(StoryEpisode::getAvatarStage)
                .max()
                .orElse(DEFAULT_AVATAR_STAGE);
    }
}
