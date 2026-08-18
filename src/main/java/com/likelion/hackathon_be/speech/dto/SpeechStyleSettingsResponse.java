package com.likelion.hackathon_be.speech.dto;

public record SpeechStyleSettingsResponse(
        String speechLevel,
        String sentenceLength,
        String directness,
        String warmth,
        String playfulness,
        String emotionalIntensity,
        boolean profanityEnabled
) {
}
