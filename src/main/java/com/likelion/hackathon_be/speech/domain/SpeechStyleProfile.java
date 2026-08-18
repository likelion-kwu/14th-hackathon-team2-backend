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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "speech_style_profiles")
public class SpeechStyleProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SpeechSourceType sourceType;

    @Column(name = "preset_code", length = 40)
    private String presetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "speech_level", nullable = false, length = 20)
    private SpeechLevel speechLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentence_length", nullable = false, length = 20)
    private SentenceLength sentenceLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "directness", nullable = false, length = 10)
    private SpeechAttributeLevel directness;

    @Enumerated(EnumType.STRING)
    @Column(name = "warmth", nullable = false, length = 10)
    private SpeechAttributeLevel warmth;

    @Enumerated(EnumType.STRING)
    @Column(name = "playfulness", nullable = false, length = 10)
    private SpeechAttributeLevel playfulness;

    @Enumerated(EnumType.STRING)
    @Column(name = "emotional_intensity", nullable = false, length = 10)
    private SpeechAttributeLevel emotionalIntensity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "style_json", nullable = false, columnDefinition = "jsonb")
    private String styleJson;

    @Column(name = "profanity_detected", nullable = false)
    private boolean profanityDetected;

    @Column(name = "profanity_enabled", nullable = false)
    private boolean profanityEnabled;

    @Column(name = "valid_message_count")
    private Integer validMessageCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpeechStyleProfile() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SpeechSourceType getSourceType() {
        return sourceType;
    }

    public String getPresetCode() {
        return presetCode;
    }

    public SpeechLevel getSpeechLevel() {
        return speechLevel;
    }

    public SentenceLength getSentenceLength() {
        return sentenceLength;
    }

    public SpeechAttributeLevel getDirectness() {
        return directness;
    }

    public SpeechAttributeLevel getWarmth() {
        return warmth;
    }

    public SpeechAttributeLevel getPlayfulness() {
        return playfulness;
    }

    public SpeechAttributeLevel getEmotionalIntensity() {
        return emotionalIntensity;
    }

    public String getStyleJson() {
        return styleJson;
    }

    public boolean isProfanityDetected() {
        return profanityDetected;
    }

    public boolean isProfanityEnabled() {
        return profanityEnabled;
    }

    public Integer getValidMessageCount() {
        return validMessageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
