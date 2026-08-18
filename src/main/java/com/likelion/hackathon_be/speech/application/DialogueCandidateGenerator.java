package com.likelion.hackathon_be.speech.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DialogueCandidateGenerator {
    private static final String PROMPT_VERSION = "speech-dialogues-v1";
    private static final Set<String> PROFANITY = Set.of("씨발", "시발", "ㅅㅂ", "병신", "개새끼", "좆");
    private static final Set<String> ALWAYS_FORBIDDEN = Set.of(
            "죽어", "자해", "목숨", "협박", "혐오", "쓸모없", "인간도 아니", "없어져"
    );
    private static final Pattern PII = Pattern.compile(
            "(?i)(?:https?://|www\\.|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|"
                    + "01[016789][- ]?\\d{3,4}[- ]?\\d{4}|(?:\\d[- ]?){9,15}\\d)"
    );

    private final OpenAiGateway gateway;
    private final ObjectMapper objectMapper;
    private final SafeDialogueCatalog safeDialogues;

    public DialogueCandidateGenerator(
            OpenAiGateway gateway,
            ObjectMapper objectMapper,
            SafeDialogueCatalog safeDialogues
    ) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.safeDialogues = safeDialogues;
    }

    public List<DialogueCandidate> generate(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        if (!gateway.isAvailable()) {
            return safeDialogues.all();
        }
        try {
            JsonNode response = gateway.structuredResponse(
                    "avatar_dialogues",
                    PROMPT_VERSION,
                    instructions(),
                    generationInput(profile, userName, allowedProfanity),
                    List.of(),
                    batchSchema(),
                    3600
            );
            return validateAndRepair(response, profile, userName, allowedProfanity);
        } catch (RuntimeException ignored) {
            return safeDialogues.all();
        }
    }

    private List<DialogueCandidate> validateAndRepair(
            JsonNode response,
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        Map<DialogueSituation, List<String>> proposed = parseBatch(response);
        Map<String, String> failures = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (DialogueSituation situation : DialogueSituation.values()) {
            int nameCount = 0;
            List<String> lines = proposed.getOrDefault(situation, List.of());
            for (int index = 0; index < 5; index++) {
                String line = index < lines.size() ? lines.get(index) : null;
                if (line != null && containsName(line, userName)) {
                    nameCount++;
                }
                if (!valid(line, userName, nameCount, used, profile.examples(), allowedProfanity)) {
                    failures.put(key(situation, index), "invalid");
                } else {
                    used.add(normalize(line));
                }
            }
        }

        Map<String, String> repairs = failures.isEmpty()
                ? Map.of()
                : requestRepairs(failures.keySet(), profile, userName, allowedProfanity);
        List<DialogueCandidate> result = new ArrayList<>();
        used.clear();
        for (DialogueSituation situation : DialogueSituation.values()) {
            int nameCount = 0;
            List<String> lines = proposed.getOrDefault(situation, List.of());
            for (int index = 0; index < 5; index++) {
                String candidate = index < lines.size() ? lines.get(index) : null;
                if (failures.containsKey(key(situation, index))) {
                    candidate = repairs.get(key(situation, index));
                }
                int nextNameCount = nameCount + (containsName(candidate, userName) ? 1 : 0);
                if (!valid(candidate, userName, nextNameCount, used, profile.examples(), allowedProfanity)) {
                    candidate = safeDialogues.line(situation, index);
                    nextNameCount = nameCount;
                }
                nameCount = nextNameCount;
                used.add(normalize(candidate));
                result.add(new DialogueCandidate(
                        situation,
                        candidate,
                        containsName(candidate, userName),
                        containsAny(candidate, PROFANITY)
                ));
            }
        }
        return List.copyOf(result);
    }

    private Map<String, String> requestRepairs(
            Set<String> failureKeys,
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        try {
            JsonNode response = gateway.structuredResponse(
                    "avatar_dialogue_repairs",
                    PROMPT_VERSION,
                    instructions(),
                    generationInput(profile, userName, allowedProfanity)
                            + "\nRegenerate only these situation:index positions: " + String.join(",", failureKeys),
                    List.of(),
                    repairSchema(failureKeys.size()),
                    Math.max(300, failureKeys.size() * 80)
            );
            Map<String, String> repairs = new HashMap<>();
            for (JsonNode repair : response.path("repairs")) {
                DialogueSituation situation = DialogueSituation.valueOf(repair.path("situation").asText());
                int index = repair.path("index").asInt(-1);
                if (index >= 0 && index < 5) {
                    repairs.put(key(situation, index), repair.path("content").asText());
                }
            }
            return repairs;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private Map<DialogueSituation, List<String>> parseBatch(JsonNode response) {
        Map<DialogueSituation, List<String>> parsed = new EnumMap<>(DialogueSituation.class);
        for (JsonNode group : response.path("dialogues")) {
            try {
                DialogueSituation situation = DialogueSituation.valueOf(group.path("situation").asText());
                List<String> lines = new ArrayList<>();
                group.path("lines").forEach(line -> lines.add(line.asText()));
                parsed.putIfAbsent(situation, List.copyOf(lines));
            } catch (IllegalArgumentException ignored) {
                // Invalid groups are repaired or replaced with the safe catalog.
            }
        }
        return parsed;
    }

    private boolean valid(
            String line,
            String userName,
            int nameCount,
            Set<String> used,
            List<SpeechExampleCandidate> examples,
            Set<String> allowedProfanity
    ) {
        if (line == null || line.isBlank() || line.length() > 50 || nameCount > 1) {
            return false;
        }
        String normalized = normalize(line);
        if (used.contains(normalized) || containsAny(line, ALWAYS_FORBIDDEN) || PII.matcher(line).find()) {
            return false;
        }
        for (String profanity : PROFANITY) {
            if (line.contains(profanity) && !allowedProfanity.contains(profanity)) {
                return false;
            }
        }
        for (SpeechExampleCandidate example : examples) {
            String source = normalize(example.content());
            if (source.length() >= 8 && (normalized.equals(source) || normalized.contains(source))) {
                return false;
            }
        }
        return true;
    }

    private JsonNode batchSchema() {
        return objectMapper.readTree("""
                {
                  "type":"object","additionalProperties":false,
                  "properties":{"dialogues":{"type":"array","minItems":8,"maxItems":8,"items":{
                    "type":"object","additionalProperties":false,
                    "properties":{
                      "situation":{"type":"string","enum":[
                        "ROUTINE_UPCOMING","ROUTINE_AVAILABLE","ROUTINE_REMINDER","ROUTINE_COMPLETED",
                        "ALL_COMPLETED","STREAK_CONTINUED","STREAK_BROKEN","RETURN_AFTER_ABSENCE"
                      ]},
                      "lines":{"type":"array","minItems":5,"maxItems":5,"items":{"type":"string"}}
                    },
                    "required":["situation","lines"]
                  }}},
                  "required":["dialogues"]
                }
                """);
    }

    private JsonNode repairSchema(int count) {
        return objectMapper.readTree("""
                {
                  "type":"object","additionalProperties":false,
                  "properties":{"repairs":{"type":"array","minItems":%d,"maxItems":%d,"items":{
                    "type":"object","additionalProperties":false,
                    "properties":{
                      "situation":{"type":"string","enum":[
                        "ROUTINE_UPCOMING","ROUTINE_AVAILABLE","ROUTINE_REMINDER","ROUTINE_COMPLETED",
                        "ALL_COMPLETED","STREAK_CONTINUED","STREAK_BROKEN","RETURN_AFTER_ABSENCE"
                      ]},
                      "index":{"type":"integer","minimum":0,"maximum":4},
                      "content":{"type":"string"}
                    },
                    "required":["situation","index","content"]
                  }}},
                  "required":["repairs"]
                }
                """.formatted(count, count));
    }

    private String instructions() {
        return """
                Generate safe Korean avatar lines for a supportive future-self character. Produce exactly five
                distinct lines for each of eight situations, at most 50 characters including spaces. Use the user
                name at most once per situation. Never include hate, threats, self-harm encouragement, personal
                attacks, appearance/health shaming, or unsupported profanity. Do not closely copy user examples.
                Return only the strict schema.
                """;
    }

    private String generationInput(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        StringBuilder examples = new StringBuilder();
        profile.examples().stream().limit(20).forEach(example -> examples
                .append(example.category().name()).append(':').append(example.content()).append('\n'));
        return "User display name: " + (userName == null ? "" : userName)
                + "\nSettings: " + profile.settings()
                + "\nStyle JSON: " + profile.styleJson()
                + "\nAllowed profanity: " + allowedProfanity
                + "\nRepresentative examples:\n" + examples;
    }

    private boolean containsName(String line, String userName) {
        return line != null && userName != null && !userName.isBlank() && line.contains(userName);
    }

    private boolean containsAny(String line, Set<String> terms) {
        if (line == null) {
            return false;
        }
        return terms.stream().anyMatch(line::contains);
    }

    private String normalize(String line) {
        return line == null ? "" : line.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String key(DialogueSituation situation, int index) {
        return situation.name() + ":" + index;
    }
}
