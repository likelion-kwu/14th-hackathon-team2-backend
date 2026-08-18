package com.likelion.hackathon_be.routine.verification.repository;

import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineVerificationRepository extends JpaRepository<RoutineVerification, Long> {

    List<RoutineVerification> findByDailyRoutineIdIn(Collection<Long> dailyRoutineIds);
}
