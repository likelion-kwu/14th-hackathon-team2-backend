package com.likelion.hackathon_be.avatar.dto;

public record RegenerateAvatarResponse(
        Long id,
        String growthTrack,
        int stage,
        String imageEndpoint,
        String assetSource,
        int regenerationRemaining,
        boolean replaced
) {
}
