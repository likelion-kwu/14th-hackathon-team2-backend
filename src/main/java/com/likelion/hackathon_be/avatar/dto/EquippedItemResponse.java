package com.likelion.hackathon_be.avatar.dto;

public record EquippedItemResponse(
        Long itemId,
        String type,
        String assetKey
) {
}
