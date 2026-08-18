package com.likelion.hackathon_be.record.application;

import com.likelion.hackathon_be.record.dto.RecordResponse;
import java.time.LocalDate;

public interface RecordService {

    RecordResponse getRecords(LocalDate fromDate, LocalDate toDate);
}
