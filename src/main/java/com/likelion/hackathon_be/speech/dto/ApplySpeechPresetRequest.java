package com.likelion.hackathon_be.speech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplySpeechPresetRequest(
        @NotBlank
        @Size(max = 40)
        String presetCode
) {
}
