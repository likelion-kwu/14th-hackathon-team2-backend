package com.likelion.hackathon_be.routine.repository;

import com.likelion.hackathon_be.routine.domain.Routine;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

    List<Routine> findByUserIdAndDeletedAtIsNullOrderByIdAsc(Long userId);

    Optional<Routine> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Routine> findByUserIdAndCategoryAndDeletedAtIsNullOrderByIdAsc(
            Long userId,
            RoutineCategory category
    );

    List<Routine> findByUserIdAndEffectiveFromLessThanEqualAndDeletedAtIsNullOrderByIdAsc(
            Long userId,
            LocalDate effectiveFrom
    );
}
