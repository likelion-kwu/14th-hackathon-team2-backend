package com.likelion.hackathon_be.routine.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyRoutineListResponse(
        LocalDate serviceDate,
        DayStatus dayStatus,
        int completedCount,
        int totalCount,
        int percentage,
        List<DailyRoutineResponse> routines
) {
}
