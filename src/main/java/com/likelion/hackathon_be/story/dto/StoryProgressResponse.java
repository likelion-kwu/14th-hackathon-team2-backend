package com.likelion.hackathon_be.story.dto;

import java.util.List;

public record StoryProgressResponse(
        int currentStreakDays,
        int maxAchievedStreakDays,
        int avatarStage,
        List<StoryEpisodeResponse> episodes
) {
}
