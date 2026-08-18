package com.likelion.hackathon_be.record.dto;

public record RecordSummaryResponse(
        int scheduledRoutineCount,
        int completedRoutineCount,
        int completionRate,
        int photoVerificationCount,
        int checkVerificationCount,
        int totalSuccessDays,
        int currentStreakDays,
        int maxAchievedStreakDays
) {
}
