package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.Routine;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    List<Routine> findByUserIdAndEffectiveFromLessThanEqualAndDeletedAtIsNullOrderByIdAsc(
            Long userId,
            LocalDate effectiveFrom
    );
}
