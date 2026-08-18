package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySuccessRecordRepository extends JpaRepository<DailySuccessRecord, Long> {

    boolean existsByUserIdAndServiceDate(Long userId, LocalDate serviceDate);

    Optional<DailySuccessRecord> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);
}
