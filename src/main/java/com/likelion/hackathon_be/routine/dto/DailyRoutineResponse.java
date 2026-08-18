package com.likelion.hackathon_be.routine.dto;

import java.time.LocalTime;
import java.time.OffsetDateTime;

public record DailyRoutineResponse(
        Long id,
        Long routineId,
        String category,
        String content,
        LocalTime startTime,
        LocalTime endTime,
        OffsetDateTime actualStartAt,
        OffsetDateTime actualEndAtExclusive,
        String verificationObject,
        DailyRoutineStatus status,
        DailyRoutineVerificationResponse verification,
        DailyRoutinePointClaimStatusResponse pointClaim
) {
}
