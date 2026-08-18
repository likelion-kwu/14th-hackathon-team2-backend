package com.likelion.hackathon_be.user.dto;

import java.time.OffsetDateTime;

public record CurrentUserResponse(
        Long id,
        String nickname,
        boolean avatarConfigured,
        boolean speechStyleConfigured,
        String nextStep,
        OffsetDateTime createdAt
) {
}
