package com.likelion.hackathon_be.speech.infrastructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.speech.application.SpeechExampleCandidate;
import com.likelion.hackathon_be.speech.application.SpeechProfileCandidate;
import com.likelion.hackathon_be.speech.application.SpeechStyleSettings;
import com.likelion.hackathon_be.speech.domain.SentenceLength;
import com.likelion.hackathon_be.speech.domain.SpeechAttributeLevel;
import com.likelion.hackathon_be.speech.domain.SpeechExampleCategory;
import com.likelion.hackathon_be.speech.domain.SpeechExampleSourceType;
import com.likelion.hackathon_be.speech.domain.SpeechLevel;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiSpeechStyleAnalyzer {
    private static final String PROMPT_VERSION = "kakao-style-analysis-v1";
    private static final Set<String> FORBIDDEN = Set.of("죽어", "자해", "협박", "혐오", "쓸모없", "병신");
    private static final Set<String> SAFE_SELF_DIRECTED_PROFANITY = Set.of("씨발", "시발", "ㅅㅂ");
    private static final Pattern PII = Pattern.compile(
            "(?i)(?:https?://|www\\.|[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|"
                    + "01[016789][- ]?\\d{3,4}[- ]?\\d{4}|(?:\\d[- ]?){9,15}\\d)"
    );

    private final OpenAiGateway gateway;
    private final ObjectMapper objectMapper;
    private final JsonNode analysisSchema;

    public OpenAiSpeechStyleAnalyzer(OpenAiGateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.analysisSchema = objectMapper.readTree(analysisSchemaText());
    }

    public AnalyzedSpeechProfile analyze(PreprocessedSpeechData data) {
        if (!gateway.isAvailable()) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        ParsedAnalysis parsed = requestValidated(
                "speech_style_analysis",
                analysisInstructions(),
                firstInput(data),
                analysisSchema,
                3000,
                response -> parseAnalysis(response, data)
        );
        List<SpeechExampleCandidate> examples = examplesFromCandidates(parsed.candidates);
        SpeechProfileCandidate candidate = new SpeechProfileCandidate(
                SpeechSourceType.KAKAO_CHAT,
                null,
                parsed.settings,
                parsed.styleJson,
                !data.observedProfanity().isEmpty(),
                data.validMessageCount(),
                examples
        );
        Set<String> allowed = new HashSet<>(data.observedProfanity());
        allowed.retainAll(SAFE_SELF_DIRECTED_PROFANITY);
        return new AnalyzedSpeechProfile(candidate, allowed);
    }

    private <T> T requestValidated(
            String schemaName,
            String instructions,
            String input,
            JsonNode schema,
            int maxTokens,
            Function<JsonNode, T> validator
    ) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                JsonNode response = gateway.structuredResponse(
                        schemaName,
                        PROMPT_VERSION,
                        instructions,
                        attempt == 0 ? input : input + "\nThe previous output was invalid. Follow the schema exactly.",
                        List.of(),
                        schema,
                        maxTokens
                );
                return validator.apply(response);
            } catch (OpenAiGatewayException exception) {
                last = exception;
                if (exception.kind() != OpenAiGatewayException.Kind.INVALID_RESPONSE) {
                    throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
                }
            } catch (BusinessException exception) {
                last = exception;
                if (exception.getErrorCode() != ErrorCode.AI_RESPONSE_INVALID) {
                    throw exception;
                }
            } catch (RuntimeException exception) {
                last = exception;
            }
        }
        throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, last == null ? "Invalid AI response" : last.getMessage());
    }

    private ParsedAnalysis parseAnalysis(JsonNode root, PreprocessedSpeechData data) {
        try {
            JsonNode profile = root.path("profile");
            SpeechStyleSettings settings = new SpeechStyleSettings(
                    SpeechLevel.valueOf(requiredText(profile, "speechLevel")),
                    SentenceLength.valueOf(requiredText(profile, "sentenceLength")),
                    SpeechAttributeLevel.valueOf(requiredText(profile, "directness")),
                    SpeechAttributeLevel.valueOf(requiredText(profile, "warmth")),
                    SpeechAttributeLevel.valueOf(requiredText(profile, "playfulness")),
                    SpeechAttributeLevel.valueOf(requiredText(profile, "emotionalIntensity")),
                    false
            );
            Map<String, PreprocessedSpeechMessage> byId = new HashMap<>();
            data.messages().forEach(message -> byId.put(message.id(), message));
            List<Map<String, String>> candidates = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            Set<String> candidateContents = new HashSet<>();
            for (JsonNode candidate : root.path("candidates")) {
                try {
                    String id = requiredText(candidate, "messageId");
                    String category = requiredText(candidate, "category");
                    SpeechExampleCategory.valueOf(category);
                    PreprocessedSpeechMessage message = byId.get(id);
                    if (message != null
                            && ids.add(id)
                            && candidateContents.add(normalize(message.userMessage()))) {
                        candidates.add(Map.of(
                                "messageId", id,
                                "category", category,
                                "content", message.userMessage()
                        ));
                    }
                } catch (RuntimeException ignored) {
                    // One malformed candidate must not fail the whole speech analysis.
                }
                if (candidates.size() == 60) {
                    break;
                }
            }
            String styleJson = sanitizedStyleJson(profile, data);
            return new ParsedAnalysis(settings, styleJson, List.copyOf(candidates));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private List<SpeechExampleCandidate> examplesFromCandidates(
            List<Map<String, String>> candidates
    ) {
        List<SpeechExampleCandidate> examples = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (Map<String, String> candidate : candidates) {
            try {
                String content = candidate.get("content").trim();
                String normalized = normalize(content);
                SpeechExampleCategory category = SpeechExampleCategory.valueOf(candidate.get("category"));
                if (content.isEmpty() || codePointLength(content) > 50 || containsForbidden(content)
                        || PII.matcher(content).find() || !unique.add(normalized)) {
                    continue;
                }
                examples.add(new SpeechExampleCandidate(
                        category,
                        SpeechExampleSourceType.USER_MESSAGE,
                        content
                ));
                if (examples.size() == 20) {
                    break;
                }
            } catch (RuntimeException ignored) {
                // Skip only the invalid candidate and keep any other usable examples.
            }
        }
        return List.copyOf(examples);
    }

    private String sanitizedStyleJson(JsonNode profile, PreprocessedSpeechData data) {
        Map<String, Object> style = new LinkedHashMap<>();
        for (String field : List.of("openingPatterns", "endingPatterns", "reactionPatterns", "avoidPatterns")) {
            style.put(field, safePatternValues(profile.path(field)));
        }
        Map<String, String> punctuation = new LinkedHashMap<>();
        JsonNode punctuationNode = profile.path("punctuationStyle");
        for (String field : List.of("period", "questionMark", "exclamationMark", "repetition")) {
            punctuation.put(field, requiredText(punctuationNode, field));
        }
        style.put("punctuationStyle", punctuation);
        Set<String> safeObserved = new HashSet<>(data.observedProfanity());
        safeObserved.retainAll(SAFE_SELF_DIRECTED_PROFANITY);
        style.put("profanity", Map.of(
                "detected", !data.observedProfanity().isEmpty(),
                "enabledByUser", false,
                "observedFrequency", profanityFrequency(data),
                "allowedExpressions", safeObserved
        ));
        style.put("personalInsultAllowed", false);
        return objectMapper.writeValueAsString(style);
    }

    private String firstInput(PreprocessedSpeechData data) {
        return objectMapper.writeValueAsString(Map.of(
                "messages", data.messages(),
                "repetitionCounts", data.repetitionCounts(),
                "serverObservedProfanityCounts", data.observedProfanityCounts()
        ));
    }

    private boolean containsForbidden(String content) {
        return FORBIDDEN.stream().anyMatch(content::contains);
    }

    private List<String> safePatternValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("Style pattern must be text");
            }
            String text = value.asText().trim();
            String normalized = normalize(text);
            if (text.isBlank() || codePointLength(text) > 50 || PII.matcher(text).find()
                    || containsForbidden(text) || !unique.add(normalized)) {
                throw new IllegalArgumentException("Invalid style pattern");
            }
            values.add(text);
        }
        return List.copyOf(values);
    }

    private String profanityFrequency(PreprocessedSpeechData data) {
        int count = data.observedProfanityCounts().values().stream().mapToInt(Integer::intValue).sum();
        if (count == 0) {
            return "NONE";
        }
        double ratio = (double) count / Math.max(1, data.validMessageCount());
        if (ratio <= 0.01) {
            return "LOW";
        }
        return ratio <= 0.05 ? "MEDIUM" : "HIGH";
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return value.asText();
    }

    private String analysisInstructions() {
        return """
                Analyze Korean writing style only. Context is only for understanding userMessage; never learn or
                copy the context speaker's style. Do not infer personality, identity, health, mental state, age,
                gender, ethnicity, or other sensitive traits. Classify representative user message IDs, and never
                invent profanity. Return only the strict schema.
                """;
    }

    private String analysisSchemaText() {
        return """
                {
                  "type":"object","additionalProperties":false,
                  "properties":{
                    "profile":{"type":"object","additionalProperties":false,"properties":{
                      "speechLevel":{"type":"string","enum":["BANMAL","JONDAEMAL"]},
                      "sentenceLength":{"type":"string","enum":["SHORT","MEDIUM","LONG"]},
                      "directness":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                      "warmth":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                      "playfulness":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                      "emotionalIntensity":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                      "openingPatterns":{"type":"array","maxItems":10,"items":{"type":"string"}},
                      "endingPatterns":{"type":"array","maxItems":10,"items":{"type":"string"}},
                      "reactionPatterns":{"type":"array","maxItems":10,"items":{"type":"string"}},
                      "avoidPatterns":{"type":"array","maxItems":10,"items":{"type":"string"}},
                      "punctuationStyle":{"type":"object","additionalProperties":false,"properties":{
                        "period":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                        "questionMark":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                        "exclamationMark":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},
                        "repetition":{"type":"string","enum":["LOW","MEDIUM","HIGH"]}
                      },"required":["period","questionMark","exclamationMark","repetition"]}
                    },"required":["speechLevel","sentenceLength","directness","warmth","playfulness",
                      "emotionalIntensity","openingPatterns","endingPatterns","reactionPatterns","avoidPatterns",
                      "punctuationStyle"]},
                    "candidates":{"type":"array","maxItems":60,"items":{"type":"object",
                      "additionalProperties":false,"properties":{
                        "messageId":{"type":"string"},
                        "category":{"type":"string","enum":["QUESTION","AGREEMENT","DISAGREEMENT",
                          "ENCOURAGEMENT","REACTION","GENERAL"]}
                      },"required":["messageId","category"]}}
                  },
                  "required":["profile","candidates"]
                }
                """;
    }

    private record ParsedAnalysis(
            SpeechStyleSettings settings,
            String styleJson,
            List<Map<String, String>> candidates
    ) {
    }
}
