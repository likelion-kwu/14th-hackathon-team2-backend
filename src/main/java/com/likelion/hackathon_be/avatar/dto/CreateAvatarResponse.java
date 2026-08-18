package com.likelion.hackathon_be.avatar.dto;

public record CreateAvatarResponse(
        Long id,
        boolean created,
        String growthTrack,
        int stage,
        String imageEndpoint,
        String assetSource,
        boolean fallbackUsed,
        int regenerationRemaining,
        String nextStep
) {
}
