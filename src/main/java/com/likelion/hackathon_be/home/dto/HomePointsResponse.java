package com.likelion.hackathon_be.home.dto;

public record HomePointsResponse(
        int totalEarned,
        int currentMonthEarned,
        int todayClaimedCount,
        int todayClaimLimit
) {
}
