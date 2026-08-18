package com.likelion.hackathon_be.routine.dto;

public record UnlockedItemResponse(
        Long id,
        String name,
        String type,
        String assetKey
) {
}
