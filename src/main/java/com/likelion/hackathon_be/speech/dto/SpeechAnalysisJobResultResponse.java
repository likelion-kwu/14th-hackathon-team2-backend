package com.likelion.hackathon_be.speech.dto;

public record SpeechAnalysisJobResultResponse(
        String sourceType,
        int dialogueCount,
        Integer validMessageCount
) {
}
