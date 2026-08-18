package com.likelion.hackathon_be.speech.infrastructure;

import java.util.Set;

import com.likelion.hackathon_be.speech.application.SpeechProfileCandidate;

public record AnalyzedSpeechProfile(SpeechProfileCandidate profile, Set<String> allowedProfanity) {
    public AnalyzedSpeechProfile {
        allowedProfanity = Set.copyOf(allowedProfanity);
    }
}
