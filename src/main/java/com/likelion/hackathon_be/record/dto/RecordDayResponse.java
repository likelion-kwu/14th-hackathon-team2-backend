package com.likelion.hackathon_be.record.dto;

import java.time.LocalDate;
import java.util.List;

public record RecordDayResponse(
        LocalDate serviceDate,
        String dayStatus,
        int completedCount,
        int totalCount,
        List<RecordRoutineResponse> routines
) {
}
