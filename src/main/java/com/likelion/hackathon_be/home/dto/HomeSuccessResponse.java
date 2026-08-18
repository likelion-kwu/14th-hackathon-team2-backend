package com.likelion.hackathon_be.home.dto;

public record HomeSuccessResponse(
        int totalSuccessDays,
        int currentStreakDays,
        int maxAchievedStreakDays
) {
}
