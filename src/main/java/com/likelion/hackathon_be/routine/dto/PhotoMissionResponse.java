package com.likelion.hackathon_be.routine.dto;

import java.time.OffsetDateTime;

public record PhotoMissionResponse(
        Long dailyRoutineId,
        String verificationObject,
        PhotoMissionDetailResponse mission,
        OffsetDateTime actualEndAtExclusive
) {
}
