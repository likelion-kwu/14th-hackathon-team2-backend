package com.likelion.hackathon_be.record.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.record.dto.RecordResponse;
import java.time.LocalDate;

public class NotImplementedRecordService implements RecordService {

    @Override
    public RecordResponse getRecords(LocalDate fromDate, LocalDate toDate) {
        throw new FeatureNotImplementedException("Record");
    }
}
