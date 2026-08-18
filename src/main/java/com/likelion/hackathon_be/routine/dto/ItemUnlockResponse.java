package com.likelion.hackathon_be.routine.dto;

public record ItemUnlockResponse(
        boolean newlyUnlocked,
        Integer milestonePoints,
        UnlockedItemResponse item
) {
}
