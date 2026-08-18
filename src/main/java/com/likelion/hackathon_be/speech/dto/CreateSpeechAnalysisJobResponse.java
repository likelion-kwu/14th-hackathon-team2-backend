package com.likelion.hackathon_be.speech.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreateSpeechAnalysisJobResponse(
        UUID jobId,
        String status,
        List<SpeechParticipantResponse> participants,
        OffsetDateTime expiresAt
) {
}
