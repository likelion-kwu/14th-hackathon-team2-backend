package com.likelion.hackathon_be.routine.dto;

import java.time.LocalDate;

public record DayResultResponse(
        LocalDate serviceDate,
        DayStatus dayStatus,
        boolean newlySucceeded,
        int completedCount,
        int totalCount
) {
}
