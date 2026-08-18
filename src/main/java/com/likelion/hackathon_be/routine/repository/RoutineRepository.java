package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {
}
