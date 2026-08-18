package com.likelion.hackathon_be.routine.point.repository;

import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutinePointClaimRepository extends JpaRepository<RoutinePointClaim, Long> {
}
