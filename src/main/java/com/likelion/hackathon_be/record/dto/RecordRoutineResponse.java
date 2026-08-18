package com.likelion.hackathon_be.record.dto;

public record RecordRoutineResponse(
        Long dailyRoutineId,
        Long routineId,
        String content,
        String status,
        String verificationType
) {
}
