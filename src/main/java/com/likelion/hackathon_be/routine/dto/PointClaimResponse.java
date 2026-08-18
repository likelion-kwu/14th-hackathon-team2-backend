package com.likelion.hackathon_be.routine.dto;

public record PointClaimResponse(
        Long dailyRoutineId,
        int awardedPoints,
        int todayClaimedCount,
        int todayClaimLimit,
        int totalEarnedPoints,
        ItemUnlockResponse itemUnlock
) {
}
