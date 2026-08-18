package com.likelion.hackathon_be.home.dto;

import com.likelion.hackathon_be.avatar.dto.EquippedItemResponse;
import java.util.List;

public record HomeAvatarResponse(
        Long id,
        String growthTrack,
        int stage,
        String imageEndpoint,
        List<EquippedItemResponse> equippedItems
) {
}
