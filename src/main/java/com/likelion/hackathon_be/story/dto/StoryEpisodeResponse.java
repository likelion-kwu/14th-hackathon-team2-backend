package com.likelion.hackathon_be.story.dto;

import java.time.OffsetDateTime;

public record StoryEpisodeResponse(
        int episodeNumber,
        int requiredStreakDays,
        boolean unlocked,
        OffsetDateTime unlockedAt
) {
}
