package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.speech.domain.SentenceLength;
import com.likelion.hackathon_be.speech.domain.SpeechAttributeLevel;
import com.likelion.hackathon_be.speech.domain.SpeechLevel;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.dto.SpeechStyleSettingsResponse;

public record SpeechStyleSettings(
        SpeechLevel speechLevel,
        SentenceLength sentenceLength,
        SpeechAttributeLevel directness,
        SpeechAttributeLevel warmth,
        SpeechAttributeLevel playfulness,
        SpeechAttributeLevel emotionalIntensity,
        boolean profanityEnabled
) {
    public static SpeechStyleSettings calm() {
        return new SpeechStyleSettings(
                SpeechLevel.BANMAL,
                SentenceLength.SHORT,
                SpeechAttributeLevel.MEDIUM,
                SpeechAttributeLevel.MEDIUM,
                SpeechAttributeLevel.LOW,
                SpeechAttributeLevel.MEDIUM,
                false
        );
    }

    public static SpeechStyleSettings from(SpeechStyleProfile profile) {
        return new SpeechStyleSettings(
                profile.getSpeechLevel(),
                profile.getSentenceLength(),
                profile.getDirectness(),
                profile.getWarmth(),
                profile.getPlayfulness(),
                profile.getEmotionalIntensity(),
                profile.isProfanityEnabled()
        );
    }

    public SpeechStyleSettingsResponse toResponse() {
        return new SpeechStyleSettingsResponse(
                speechLevel.name(),
                sentenceLength.name(),
                directness.name(),
                warmth.name(),
                playfulness.name(),
                emotionalIntensity.name(),
                profanityEnabled
        );
    }
}
