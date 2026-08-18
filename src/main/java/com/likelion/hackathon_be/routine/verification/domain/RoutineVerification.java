package com.likelion.hackathon_be.routine.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "routine_verifications")
public class RoutineVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "daily_routine_id", nullable = false)
    private Long dailyRoutineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 10)
    private VerificationType verificationType;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RoutineVerification() {
    }

    public static RoutineVerification create(
            Long dailyRoutineId,
            VerificationType verificationType,
            Instant verifiedAt
    ) {
        RoutineVerification verification = new RoutineVerification();
        verification.dailyRoutineId = dailyRoutineId;
        verification.verificationType = verificationType;
        verification.verifiedAt = verifiedAt;
        verification.createdAt = verifiedAt;
        return verification;
    }

    public Long getId() {
        return id;
    }

    public Long getDailyRoutineId() {
        return dailyRoutineId;
    }

    public VerificationType getVerificationType() {
        return verificationType;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
