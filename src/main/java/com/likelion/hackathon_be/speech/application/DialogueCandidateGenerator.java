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
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
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
        return generate(profile, userName, allowedProfanity, true);
    }

    public List<DialogueCandidate> generateStrict(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        return generate(profile, userName, allowedProfanity, false);
    }

    public List<DialogueCandidate> generateWithSafeFallback(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        return generate(profile, userName, allowedProfanity);
    }

    private List<DialogueCandidate> generate(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity,
            boolean allowWholeBatchFallback
    ) {
        Set<String> effectiveProfanity = profile.settings().profanityEnabled()
                ? Set.copyOf(allowedProfanity)
                : Set.of();
        if (!gateway.isAvailable()) {
            return fallbackOrThrow(allowWholeBatchFallback, null);
        }
        try {
            JsonNode response = requestBatch(profile, userName, effectiveProfanity);
            return validateAndRepair(response, profile, userName, effectiveProfanity);
        } catch (RuntimeException exception) {
            return fallbackOrThrow(allowWholeBatchFallback, exception);
        }
    }

    private JsonNode requestBatch(
            SpeechProfileCandidate profile,
            String userName,
            Set<String> allowedProfanity
    ) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = gateway.structuredResponse(
                        "avatar_dialogues",
                        PROMPT_VERSION,
                        instructions(),
                        generationInput(profile, userName, allowedProfanity)
                                + (attempt == 0 ? "" : "\nThe previous output was invalid. Follow the schema exactly."),
                        List.of(),
                        batchSchema(),
                        3600
                );
                if (!hasExactBatchShape(response)) {
                    last = new OpenAiGatewayException(
                            OpenAiGatewayException.Kind.INVALID_RESPONSE,
                            "Dialogue batch does not match the required 8x5 shape"
                    );
                    continue;
                }
                return response;
            } catch (OpenAiGatewayException exception) {
                last = exception;
                if (exception.kind() != OpenAiGatewayException.Kind.INVALID_RESPONSE) {
                    throw exception;
                }
            }
        }
        throw new BusinessException(
                ErrorCode.DIALOGUE_GENERATION_FAILED,
                last == null ? ErrorCode.DIALOGUE_GENERATION_FAILED.defaultMessage() : last.getMessage()
        );
    }

    private boolean hasExactBatchShape(JsonNode response) {
        JsonNode groups = response.path("dialogues");
        if (!groups.isArray() || groups.size() != DialogueSituation.values().length) {
            return false;
        }
        Set<DialogueSituation> situations = new HashSet<>();
        for (JsonNode group : groups) {
            try {
                DialogueSituation situation = DialogueSituation.valueOf(group.path("situation").asText());
                JsonNode lines = group.path("lines");
                if (!situations.add(situation) || !lines.isArray() || lines.size() != 5) {
                    return false;
                }
                for (JsonNode line : lines) {
                    if (!line.isTextual()) {
                        return false;
                    }
                }
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return situations.size() == DialogueSituation.values().length;
    }

    private List<DialogueCandidate> fallbackOrThrow(boolean allowed, RuntimeException cause) {
        if (allowed) {
            return safeDialogues.all();
        }
        throw new BusinessException(
                ErrorCode.DIALOGUE_GENERATION_FAILED,
                cause == null ? ErrorCode.DIALOGUE_GENERATION_FAILED.defaultMessage() : cause.getMessage()
        );
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
                if (!valid(situation, line, userName, nameCount, used, profile.examples(), allowedProfanity)) {
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
                if (!valid(
                        situation,
                        candidate,
                        userName,
                        nextNameCount,
                        used,
                        profile.examples(),
                        allowedProfanity
                )) {
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
            DialogueSituation situation,
            String line,
            String userName,
            int nameCount,
            Set<String> used,
            List<SpeechExampleCandidate> examples,
            Set<String> allowedProfanity
    ) {
        if (line == null || line.isBlank() || codePointLength(line) > 50 || nameCount > 1) {
            return false;
        }
        String normalized = normalize(line);
        if (used.contains(normalized)
                || containsAny(line, ALWAYS_FORBIDDEN)
                || PII.matcher(line).find()
                || !isSituationRelevant(situation, line)) {
            return false;
        }
        for (String profanity : PROFANITY) {
            if (line.contains(profanity) && !allowedProfanity.contains(profanity)) {
                return false;
            }
        }
        for (SpeechExampleCandidate example : examples) {
            String source = normalize(example.content());
            if (codePointLength(source) >= 8 && (normalized.equals(source) || normalized.contains(source))) {
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
        return "User display name: " + usableUserName(userName)
                + "\nSettings: " + profile.settings()
                + "\nStyle JSON: " + profile.styleJson()
                + "\nAllowed profanity: " + allowedProfanity
                + "\nRepresentative examples:\n" + examples;
    }

    private boolean containsName(String line, String userName) {
        String usable = usableUserName(userName);
        return line != null && !usable.isBlank() && line.contains(usable);
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

    private String usableUserName(String userName) {
        return userName != null && codePointLength(userName.trim()) >= 2 ? userName.trim() : "";
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private boolean isSituationRelevant(DialogueSituation situation, String line) {
        return switch (situation) {
            case ROUTINE_UPCOMING -> containsAny(line, Set.of("곧", "준비", "시작", "전에", "조금", "하나", "할 일", "숨"));
            case ROUTINE_AVAILABLE -> containsAny(line, Set.of("지금", "시작", "해보", "루틴", "할 수"));
            case ROUTINE_REMINDER -> containsAny(line, Set.of("아직", "지금", "하나", "루틴", "해보", "가능", "움직"));
            case ROUTINE_COMPLETED -> containsAny(line, Set.of("완료", "해냈", "했", "수고", "끝", "오늘", "실천", "흐름"));
            case ALL_COMPLETED -> containsAny(line, Set.of("다 ", "전부", "오늘", "완료", "끝", "쉬어"));
            case STREAK_CONTINUED -> containsAny(line, Set.of("연속", "이어", "꾸준", "계속", "유지", "흐름", "쌓", "리듬"));
            case STREAK_BROKEN -> containsAny(line, Set.of("다시", "괜찮", "쉬", "새로", "오늘", "하나"));
            case RETURN_AFTER_ABSENCE -> containsAny(line, Set.of("돌아", "다시", "왔", "오랜", "반가", "하나"));
        };
    }
}
