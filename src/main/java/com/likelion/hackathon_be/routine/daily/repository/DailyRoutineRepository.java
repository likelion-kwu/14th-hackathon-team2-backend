package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyRoutineRepository extends JpaRepository<DailyRoutine, Long> {

    List<DailyRoutine> findByRoutineIdAndServiceDateBetween(
            Long routineId,
            LocalDate fromDate,
            LocalDate toDate
    );

    List<DailyRoutine> findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(
            Long userId,
            LocalDate serviceDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d
            from DailyRoutine d
            where d.userId = :userId
              and d.serviceDate = :serviceDate
            order by d.id asc
            """)
    List<DailyRoutine> findByUserIdAndServiceDateForUpdateOrderByIdAsc(
            @Param("userId") Long userId,
            @Param("serviceDate") LocalDate serviceDate
    );
}
