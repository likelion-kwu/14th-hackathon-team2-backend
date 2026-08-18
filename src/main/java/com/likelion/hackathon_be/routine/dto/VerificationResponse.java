package com.likelion.hackathon_be.routine.dto;

import java.time.OffsetDateTime;

public record VerificationResponse(
        Long id,
        Long dailyRoutineId,
        String type,
        OffsetDateTime verifiedAt
) {
}
