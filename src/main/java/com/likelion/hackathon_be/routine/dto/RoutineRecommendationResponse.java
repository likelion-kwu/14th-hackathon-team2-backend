package com.likelion.hackathon_be.routine.dto;

public record RoutineRecommendationResponse(
        String code,
        String category,
        String content,
        String recommendedVerificationObject
) {
}
