package com.likelion.hackathon_be.speech.dto;

import jakarta.validation.constraints.NotBlank;

public record StartSpeechAnalysisRequest(
        @NotBlank
        String participantId
) {
}
