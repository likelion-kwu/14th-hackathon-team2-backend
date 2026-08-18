package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailySuccessRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailySuccessRecordRepository extends JpaRepository<DailySuccessRecord, Long> {

    boolean existsByUserIdAndServiceDate(Long userId, LocalDate serviceDate);

    Optional<DailySuccessRecord> findByUserIdAndServiceDate(Long userId, LocalDate serviceDate);

    long countByUserId(Long userId);

    @Query("""
            select record.serviceDate
            from DailySuccessRecord record
            where record.userId = :userId
              and record.serviceDate <= :throughDate
            order by record.serviceDate asc
            """)
    List<LocalDate> findServiceDatesByUserIdThroughDate(
            @Param("userId") Long userId,
            @Param("throughDate") LocalDate throughDate
    );
}
