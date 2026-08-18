package com.likelion.hackathon_be.record.dto;

import java.util.List;

public record RecordResponse(
        RecordPeriodResponse period,
        RecordSummaryResponse summary,
        List<RecordDayResponse> days
) {
}
