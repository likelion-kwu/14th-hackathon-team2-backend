package com.likelion.hackathon_be.speech.dto;

public record UpdateSpeechStyleResponse(
        SpeechStyleSettingsResponse settings,
        int dialogueCount
) {
}
