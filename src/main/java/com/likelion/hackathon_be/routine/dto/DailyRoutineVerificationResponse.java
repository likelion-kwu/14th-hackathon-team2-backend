package com.likelion.hackathon_be.routine.dto;

import java.time.OffsetDateTime;

public record DailyRoutineVerificationResponse(
        String type,
        OffsetDateTime verifiedAt
) {
}
