package com.likelion.hackathon_be.routine.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record RoutineResponse(
        Long id,
        String category,
        String content,
        LocalDate scheduledDate,
        LocalTime startTime,
        LocalTime endTime,
        String repeatType,
        List<String> daysOfWeek,
        String verificationObject,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
