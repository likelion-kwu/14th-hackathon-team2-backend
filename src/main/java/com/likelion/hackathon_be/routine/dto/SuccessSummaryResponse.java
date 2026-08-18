package com.likelion.hackathon_be.routine.dto;

public record SuccessSummaryResponse(
        int totalSuccessDays,
        int currentStreakDays,
        int maxAchievedStreakDays
) {
}
