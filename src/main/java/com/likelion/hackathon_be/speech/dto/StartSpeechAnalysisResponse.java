package com.likelion.hackathon_be.speech.dto;

import java.util.UUID;

public record StartSpeechAnalysisResponse(
        UUID jobId,
        String status,
        int pollAfterMs
) {
}
