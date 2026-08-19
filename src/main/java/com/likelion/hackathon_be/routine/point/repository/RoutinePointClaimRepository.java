package com.likelion.hackathon_be.routine.point.repository;

import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutinePointClaimRepository extends JpaRepository<RoutinePointClaim, Long> {

    List<RoutinePointClaim> findByDailyRoutineIdIn(Collection<Long> dailyRoutineIds);

    Optional<RoutinePointClaim> findByDailyRoutineId(Long dailyRoutineId);

    long countByUserIdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(
            Long userId,
            Instant fromInclusive,
            Instant toExclusive
    );

    boolean existsByDailyRoutineId(Long dailyRoutineId);

    @Query("""
            select count(pointClaim)
            from RoutinePointClaim pointClaim, DailyRoutine dailyRoutine
            where pointClaim.dailyRoutineId = dailyRoutine.id
              and pointClaim.userId = :userId
              and dailyRoutine.serviceDate = :serviceDate
            """)
    long countByUserIdAndServiceDate(
            @Param("userId") Long userId,
            @Param("serviceDate") LocalDate serviceDate
    );

    @Query("""
            select coalesce(sum(pointClaim.amount), 0)
            from RoutinePointClaim pointClaim
            where pointClaim.userId = :userId
            """)
    long sumAmountByUserId(@Param("userId") Long userId);

    @Query("""
            select coalesce(sum(pointClaim.amount), 0)
            from RoutinePointClaim pointClaim
            where pointClaim.userId = :userId
              and pointClaim.claimedAt >= :fromInclusive
              and pointClaim.claimedAt < :toExclusive
            """)
    long sumAmountByUserIdAndClaimedAtBetween(
            @Param("userId") Long userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Query("""
            select pointClaim.userId as userId,
                   coalesce(sum(pointClaim.amount), 0) as earnedPoints
            from RoutinePointClaim pointClaim
            where pointClaim.claimedAt >= :fromInclusive
              and pointClaim.claimedAt < :toExclusive
            group by pointClaim.userId
            order by coalesce(sum(pointClaim.amount), 0) desc, pointClaim.userId asc
            """)
    List<MonthlyPointSum> sumMonthlyPointsByUser(
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );
}
