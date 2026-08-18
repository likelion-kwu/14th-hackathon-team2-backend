package com.likelion.hackathon_be.routine.dto;

public record StoryUnlockResponse(
        int episodeNumber,
        int requiredStreakDays
) {
}
