package com.likelion.hackathon_be.routine.point.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "routine_point_claims")
public class RoutinePointClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "daily_routine_id", nullable = false)
    private Long dailyRoutineId;

    @Column(name = "amount", nullable = false)
    private short amount;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoutinePointClaim() {
    }

    public static RoutinePointClaim create(
            Long userId,
            Long dailyRoutineId,
            short amount,
            Instant claimedAt
    ) {
        RoutinePointClaim pointClaim = new RoutinePointClaim();
        pointClaim.userId = userId;
        pointClaim.dailyRoutineId = dailyRoutineId;
        pointClaim.amount = amount;
        pointClaim.claimedAt = claimedAt;
        pointClaim.createdAt = claimedAt;
        return pointClaim;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDailyRoutineId() {
        return dailyRoutineId;
    }

    public short getAmount() {
        return amount;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
