package com.likelion.hackathon_be.routine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "routines")
public class Routine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RoutineCategory category;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type", nullable = false, length = 20)
    private RepeatType repeatType;

    @Column(name = "verification_object", nullable = false, length = 40)
    private String verificationObject;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Routine() {
    }

    public static Routine create(
            Long userId,
            RoutineCategory category,
            String content,
            LocalTime startTime,
            LocalTime endTime,
            RepeatType repeatType,
            String verificationObject,
            LocalDate effectiveFrom,
            Instant now
    ) {
        Routine routine = new Routine();
        routine.userId = userId;
        routine.category = category;
        routine.content = content;
        routine.startTime = startTime;
        routine.endTime = endTime;
        routine.repeatType = repeatType;
        routine.verificationObject = verificationObject;
        routine.effectiveFrom = effectiveFrom;
        routine.createdAt = now;
        routine.updatedAt = now;
        return routine;
    }

    public void update(
            RoutineCategory category,
            String content,
            LocalTime startTime,
            LocalTime endTime,
            RepeatType repeatType,
            String verificationObject,
            LocalDate effectiveFrom,
            Instant now
    ) {
        this.category = category;
        this.content = content;
        this.startTime = startTime;
        this.endTime = endTime;
        this.repeatType = repeatType;
        this.verificationObject = verificationObject;
        this.effectiveFrom = effectiveFrom;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public RoutineCategory getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public RepeatType getRepeatType() {
        return repeatType;
    }

    public String getVerificationObject() {
        return verificationObject;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
