package com.likelion.hackathon_be.avatar.dto;

import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import jakarta.validation.constraints.NotNull;

public record SelectAvatarDialogueRequest(
        @NotNull
        DialogueSituation situation
) {
}
