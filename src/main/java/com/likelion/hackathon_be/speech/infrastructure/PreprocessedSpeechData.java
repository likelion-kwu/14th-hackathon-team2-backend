package com.likelion.hackathon_be.speech.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PreprocessedSpeechData(
        List<PreprocessedSpeechMessage> messages,
        int validMessageCount,
        Map<String, Integer> repetitionCounts,
        Set<String> observedProfanity
) {
    public PreprocessedSpeechData {
        messages = List.copyOf(messages);
        repetitionCounts = Map.copyOf(repetitionCounts);
        observedProfanity = Set.copyOf(observedProfanity);
    }
}
