package com.likelion.hackathon_be.home.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record HomeRoutineResponse(
        Long dailyRoutineId,
        Long routineId,
        String content,
        LocalDate serviceDate,
        LocalTime startTime,
        LocalTime endTime,
        OffsetDateTime actualStartAt,
        OffsetDateTime actualEndAtExclusive,
        String verificationObject,
        String status,
        String verificationType
) {
}
