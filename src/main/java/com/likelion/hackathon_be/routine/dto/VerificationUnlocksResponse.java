package com.likelion.hackathon_be.routine.dto;

import java.util.List;

public record VerificationUnlocksResponse(
        List<StoryUnlockResponse> stories,
        AvatarStageChangedResponse avatarStageChanged
) {
}
