package com.likelion.hackathon_be.routine.dto;

public record AvatarStageChangedResponse(
        boolean changed,
        int previousStage,
        int currentStage
) {
}
