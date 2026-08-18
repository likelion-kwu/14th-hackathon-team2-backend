package com.likelion.hackathon_be.speech.dto;

import java.time.OffsetDateTime;

public record SpeechStyleResponse(
        String sourceType,
        SpeechStyleSettingsResponse settings,
        boolean profanityDetected,
        Integer validMessageCount,
        OffsetDateTime updatedAt
) {
}
