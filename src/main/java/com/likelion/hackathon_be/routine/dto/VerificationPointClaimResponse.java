package com.likelion.hackathon_be.routine.dto;

public record VerificationPointClaimResponse(
        boolean autoAwarded,
        boolean claimable,
        int rewardPoints
) {
}
