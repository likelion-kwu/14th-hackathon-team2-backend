package com.likelion.hackathon_be.speech.domain;

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
@Table(name = "speech_style_examples")
public class SpeechStyleExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SpeechExampleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SpeechExampleSourceType sourceType;

    @Column(name = "content", nullable = false, length = 50)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SpeechStyleExample() {
    }

    public static SpeechStyleExample create(
            Long profileId,
            SpeechExampleCategory category,
            SpeechExampleSourceType sourceType,
            String content,
            Instant now
    ) {
        SpeechStyleExample example = new SpeechStyleExample();
        example.profileId = profileId;
        example.category = category;
        example.sourceType = sourceType;
        example.content = content;
        example.createdAt = now;
        return example;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public SpeechExampleCategory getCategory() {
        return category;
    }

    public SpeechExampleSourceType getSourceType() {
        return sourceType;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
