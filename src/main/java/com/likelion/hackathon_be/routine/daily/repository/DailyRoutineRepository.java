package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
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

    List<DailyRoutine> findByUserIdAndServiceDateBetweenOrderByServiceDateDescStartTimeSnapshotAscIdAsc(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    );

    List<DailyRoutine> findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(
            Long userId,
            LocalDate serviceDate
    );

    List<DailyRoutine> findByRoutineIdAndServiceDateGreaterThanEqualOrderByServiceDateAsc(
            Long routineId,
            LocalDate serviceDate
    );

    List<DailyRoutine> findByUserIdAndServiceDateBetween(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    );

    @Query("""
            select distinct d.serviceDate
            from DailyRoutine d
            where d.userId = :userId
              and d.serviceDate <= :throughDate
              and d.categorySnapshot <> :excludedCategory
            order by d.serviceDate asc
            """)
    List<LocalDate> findScheduledServiceDatesByUserIdThroughDateExcludingCategory(
            @Param("userId") Long userId,
            @Param("throughDate") LocalDate throughDate,
            @Param("excludedCategory") RoutineCategory excludedCategory
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
