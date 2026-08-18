package com.likelion.hackathon_be.avatar.dto;

public record AvatarDialogueSelectionResponse(
        Long dialogueId,
        String situation,
        String content
) {
}
