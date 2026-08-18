package com.likelion.hackathon_be.routine.daily.domain;

import com.likelion.hackathon_be.routine.domain.RoutineCategory;
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
@Table(name = "daily_routines")
public class DailyRoutine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "routine_id", nullable = false)
    private Long routineId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_snapshot", nullable = false, length = 20)
    private RoutineCategory categorySnapshot;

    @Column(name = "content_snapshot", nullable = false, length = 100)
    private String contentSnapshot;

    @Column(name = "start_time_snapshot", nullable = false)
    private LocalTime startTimeSnapshot;

    @Column(name = "end_time_snapshot", nullable = false)
    private LocalTime endTimeSnapshot;

    @Column(name = "verification_object_snapshot", nullable = false, length = 40)
    private String verificationObjectSnapshot;

    @Column(name = "mission_template_id")
    private Long missionTemplateId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyRoutine() {
    }

    public Long getId() {
        return id;
    }

    public Long getRoutineId() {
        return routineId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public RoutineCategory getCategorySnapshot() {
        return categorySnapshot;
    }

    public String getContentSnapshot() {
        return contentSnapshot;
    }

    public LocalTime getStartTimeSnapshot() {
        return startTimeSnapshot;
    }

    public LocalTime getEndTimeSnapshot() {
        return endTimeSnapshot;
    }

    public String getVerificationObjectSnapshot() {
        return verificationObjectSnapshot;
    }

    public Long getMissionTemplateId() {
        return missionTemplateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
