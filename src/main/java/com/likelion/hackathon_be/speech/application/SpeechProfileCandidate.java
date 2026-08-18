package com.likelion.hackathon_be.speech.application;

import java.util.List;

import com.likelion.hackathon_be.speech.domain.SpeechSourceType;

public record SpeechProfileCandidate(
        SpeechSourceType sourceType,
        String presetCode,
        SpeechStyleSettings settings,
        String styleJson,
        boolean profanityDetected,
        Integer validMessageCount,
        List<SpeechExampleCandidate> examples
) {
}
