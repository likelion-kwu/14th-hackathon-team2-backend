package com.likelion.hackathon_be.routine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "photo_mission_templates")
public class PhotoMissionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gesture_code", nullable = false, length = 40)
    private String gestureCode;

    @Column(name = "instruction_template", nullable = false, length = 150)
    private String instructionTemplate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PhotoMissionTemplate() {
    }

    public Long getId() {
        return id;
    }

    public String getGestureCode() {
        return gestureCode;
    }

    public String getInstructionTemplate() {
        return instructionTemplate;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
