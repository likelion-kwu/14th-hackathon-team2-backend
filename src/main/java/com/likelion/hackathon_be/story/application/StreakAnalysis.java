package com.likelion.hackathon_be.story.application;

public record StreakAnalysis(
        int totalSuccessDays,
        int currentStreakDays,
        int maxAchievedStreakDays
) {
}
