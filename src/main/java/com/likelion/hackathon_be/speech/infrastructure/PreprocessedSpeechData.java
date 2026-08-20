package com.likelion.hackathon_be.speech.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PreprocessedSpeechData(
        List<PreprocessedSpeechMessage> messages,
        int validMessageCount,
        Map<String, Integer> repetitionCounts,
        Map<String, Integer> observedProfanityCounts
) {
    public PreprocessedSpeechData {
        messages = List.copyOf(messages);
        repetitionCounts = Map.copyOf(repetitionCounts);
        observedProfanityCounts = Map.copyOf(observedProfanityCounts);
    }

    public Set<String> observedProfanity() {
        return observedProfanityCounts.keySet();
    }
}
