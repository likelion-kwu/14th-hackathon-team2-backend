package com.likelion.hackathon_be.speech.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "speech_analysis_jobs")
public class SpeechAnalysisJob {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SpeechAnalysisJobStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpeechAnalysisJob() {
    }

    public static SpeechAnalysisJob create(UUID id, Long userId, Instant expiresAt, Instant now) {
        SpeechAnalysisJob job = new SpeechAnalysisJob();
        job.id = id;
        job.userId = userId;
        job.status = SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION;
        job.expiresAt = expiresAt;
        job.createdAt = now;
        job.updatedAt = now;
        return job;
    }

    public void transitionTo(SpeechAnalysisJobStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SpeechAnalysisJobStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
