package com.likelion.hackathon_be.speech.dto;

public record ApplySpeechPresetResponse(
        String sourceType,
        String presetCode,
        SpeechStyleSettingsResponse settings,
        int dialogueCount
) {
}
