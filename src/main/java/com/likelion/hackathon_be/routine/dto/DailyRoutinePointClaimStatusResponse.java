package com.likelion.hackathon_be.routine.dto;

public record DailyRoutinePointClaimStatusResponse(
        boolean claimed,
        boolean claimable,
        int rewardPoints
) {
}
