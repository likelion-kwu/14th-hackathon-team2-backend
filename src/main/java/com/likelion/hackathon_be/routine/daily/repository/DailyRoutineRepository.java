package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRoutineRepository extends JpaRepository<DailyRoutine, Long> {

    List<DailyRoutine> findByRoutineIdAndServiceDateBetween(
            Long routineId,
            LocalDate fromDate,
            LocalDate toDate
    );
}
