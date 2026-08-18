package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySuccessRecordRepository extends JpaRepository<DailySuccessRecord, Long> {
}
