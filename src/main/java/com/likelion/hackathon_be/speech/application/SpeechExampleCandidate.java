package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.speech.domain.SpeechExampleCategory;
import com.likelion.hackathon_be.speech.domain.SpeechExampleSourceType;

public record SpeechExampleCandidate(
        SpeechExampleCategory category,
        SpeechExampleSourceType sourceType,
        String content
) {
}
