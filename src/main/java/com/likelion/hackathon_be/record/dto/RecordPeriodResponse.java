package com.likelion.hackathon_be.record.dto;

import java.time.LocalDate;

public record RecordPeriodResponse(
        LocalDate fromDate,
        LocalDate toDate
) {
}
