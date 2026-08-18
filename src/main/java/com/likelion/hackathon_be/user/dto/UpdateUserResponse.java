package com.likelion.hackathon_be.user.dto;

import java.time.OffsetDateTime;

public record UpdateUserResponse(
        Long id,
        String nickname,
        String nextStep,
        OffsetDateTime updatedAt
) {
}
