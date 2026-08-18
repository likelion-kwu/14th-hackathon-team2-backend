package com.likelion.hackathon_be.session.dto;

import java.time.OffsetDateTime;

public record CreateSessionResponse(
        String accessToken,
        OffsetDateTime expiresAt,
        SessionUserResponse user,
        String nextStep
) {
}
