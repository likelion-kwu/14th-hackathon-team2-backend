package com.likelion.hackathon_be.routine.daily.repository;

import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyRoutineRepository extends JpaRepository<DailyRoutine, Long> {
}
