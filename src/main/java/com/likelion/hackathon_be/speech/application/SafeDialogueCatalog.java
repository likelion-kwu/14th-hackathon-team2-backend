package com.likelion.hackathon_be.speech.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SafeDialogueCatalog {
    private final ObjectMapper objectMapper;
    private Map<DialogueSituation, List<String>> dialogues;

    public SafeDialogueCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        byte[] source;
        try (var input = new ClassPathResource("speech/calm-dialogues.json").getInputStream()) {
            source = input.readAllBytes();
        }
        JsonNode root = objectMapper.readTree(source);
        Map<DialogueSituation, List<String>> loaded = new EnumMap<>(DialogueSituation.class);
        for (DialogueSituation situation : DialogueSituation.values()) {
            JsonNode lines = root.path(situation.name());
            if (!lines.isArray() || lines.size() != 5) {
                throw new IllegalStateException("Safe dialogues must contain five lines per situation");
            }
            List<String> values = new ArrayList<>();
            lines.forEach(line -> values.add(line.asText()));
            loaded.put(situation, List.copyOf(values));
        }
        this.dialogues = Map.copyOf(loaded);
    }

    public String line(DialogueSituation situation, int index) {
        return dialogues.get(situation).get(index);
    }

    public List<DialogueCandidate> all() {
        List<DialogueCandidate> result = new ArrayList<>();
        for (DialogueSituation situation : DialogueSituation.values()) {
            for (String line : dialogues.get(situation)) {
                result.add(new DialogueCandidate(situation, line, false, false));
            }
        }
        return List.copyOf(result);
    }
}
