package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.RoutineRepeatDay;
import com.likelion.hackathon_be.routine.domain.RoutineRepeatDayId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineRepeatDayRepository extends JpaRepository<RoutineRepeatDay, RoutineRepeatDayId> {

    List<RoutineRepeatDay> findByIdRoutineId(Long routineId);

    List<RoutineRepeatDay> findByIdRoutineIdIn(Collection<Long> routineIds);

    @Modifying
    @Query("delete from RoutineRepeatDay repeatDay where repeatDay.id.routineId = :routineId")
    int deleteAllByRoutineId(@Param("routineId") Long routineId);
}
