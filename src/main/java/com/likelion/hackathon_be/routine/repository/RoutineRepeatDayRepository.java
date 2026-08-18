package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDayId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepeatDayRepository extends JpaRepository<RoutineRepeatDay, RoutineRepeatDayId> {

    List<RoutineRepeatDay> findByIdRoutineId(Long routineId);
}
