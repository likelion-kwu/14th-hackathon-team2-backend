package com.likelion.hackathon_be.routine.point.repository;

import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutinePointClaimRepository extends JpaRepository<RoutinePointClaim, Long> {

    List<RoutinePointClaim> findByDailyRoutineIdIn(Collection<Long> dailyRoutineIds);

    long countByUserIdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(
            Long userId,
            Instant fromInclusive,
            Instant toExclusive
    );
}
