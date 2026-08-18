package com.likelion.hackathon_be.avatar.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AvatarResponse(
        Long id,
        String growthTrack,
        int stage,
        Integer highestUnlockedEpisodeNumber,
        String imageEndpoint,
        String assetSource,
        int regenerationRemaining,
        List<EquippedItemResponse> equippedItems,
        OffsetDateTime updatedAt
) {
}
