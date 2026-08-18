package com.likelion.hackathon_be.speech.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SpeechAnalysisJobResponse(
        UUID jobId,
        String status,
        Integer pollAfterMs,
        OffsetDateTime expiresAt,
        SpeechAnalysisJobResultResponse result
) {
}
