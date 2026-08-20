package com.likelion.hackathon_be.speech.infrastructure;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.speech.application.SpeechExampleCandidate;
import com.likelion.hackathon_be.speech.domain.SpeechExampleCategory;
import com.likelion.hackathon_be.speech.domain.SpeechExampleSourceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiSpeechStyleAnalyzerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsExamplesFromAnalysisCandidatesUsingOriginalUserContent() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(List.of(
                candidate("m-001", "GENERAL"),
                candidate("m-002", "QUESTION")
        )));
        OpenAiSpeechStyleAnalyzer analyzer = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper);

        AnalyzedSpeechProfile analyzed = analyzer.analyze(data(List.of(
                message("m-001", "first original message"),
                message("m-002", "second original?")
        )));

        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::content)
                .containsExactly("first original message", "second original?");
        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::category)
                .containsExactly(SpeechExampleCategory.GENERAL, SpeechExampleCategory.QUESTION);
        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::sourceType)
                .containsOnly(SpeechExampleSourceType.USER_MESSAGE);
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @Test
    void skipsInvalidCandidateAndKeepsValidOnes() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(List.of(
                candidate("m-001", "GENERAL"),
                candidate("m-404", "QUESTION"),
                candidate("m-002", "NOT_A_CATEGORY"),
                candidate("m-003", "AGREEMENT")
        )));

        AnalyzedSpeechProfile analyzed = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper)
                .analyze(data(List.of(
                        message("m-001", "valid one"),
                        message("m-002", "invalid category source"),
                        message("m-003", "valid three")
                )));

        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::content)
                .containsExactly("valid one", "valid three");
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @Test
    void limitsExamplesToTwenty() throws Exception {
        List<String> candidates = new ArrayList<>();
        List<PreprocessedSpeechMessage> messages = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            String id = "m-%03d".formatted(index);
            candidates.add(candidate(id, "GENERAL"));
            messages.add(message(id, "message " + index));
        }
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(candidates));

        AnalyzedSpeechProfile analyzed = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper)
                .analyze(data(messages));

        assertThat(analyzed.profile().examples()).hasSize(20);
        assertThat(analyzed.profile().examples().get(19).content()).isEqualTo("message 20");
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @Test
    void removesDuplicatesByNormalizedContent() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(List.of(
                candidate("m-001", "GENERAL"),
                candidate("m-002", "QUESTION"),
                candidate("m-003", "AGREEMENT")
        )));

        AnalyzedSpeechProfile analyzed = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper)
                .analyze(data(List.of(
                        message("m-001", "hello there"),
                        message("m-002", "hello  there"),
                        message("m-003", "different")
                )));

        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::content)
                .containsExactly("hello there", "different");
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @Test
    void excludesPiiForbiddenBlankAndOverFiftyCodePointCandidates() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(List.of(
                candidate("m-001", "GENERAL"),
                candidate("m-002", "QUESTION"),
                candidate("m-003", "AGREEMENT"),
                candidate("m-004", "REACTION"),
                candidate("m-005", "ENCOURAGEMENT"),
                candidate("m-006", "DISAGREEMENT")
        )));

        AnalyzedSpeechProfile analyzed = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper)
                .analyze(data(List.of(
                        message("m-001", "safe example"),
                        message("m-002", "010-1234-5678"),
                        message("m-003", forbiddenTerm()),
                        message("m-004", " "),
                        message("m-005", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxy"),
                        message("m-006", "another safe")
                )));

        assertThat(analyzed.profile().examples())
                .extracting(SpeechExampleCandidate::content)
                .containsExactly("safe example", "another safe");
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @Test
    void storesOnlyExtendedJsonAndCalculatesObservedProfanityFrequency() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse(List.of(candidate("m-001", "GENERAL"))));
        OpenAiSpeechStyleAnalyzer analyzer = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper);

        AnalyzedSpeechProfile analyzed = analyzer.analyze(data(
                List.of(message("m-001", "first original message")),
                Map.of(safeProfanityTerm(), 6)
        ));
        JsonNode style = objectMapper.readTree(analyzed.profile().styleJson());

        assertThat(style.has("speechLevel")).isFalse();
        assertThat(style.path("openingPatterns").get(0).asText()).isEqualTo("hello");
        assertThat(style.path("profanity").path("observedFrequency").asText()).isEqualTo("HIGH");
        assertThat(style.path("profanity").path("enabledByUser").booleanValue()).isFalse();
        assertThat(analyzed.allowedProfanity()).containsExactly(safeProfanityTerm());
        assertThat(gateway.schemaNames).containsExactly("speech_style_analysis");
    }

    @SuppressWarnings("unchecked")
    private String forbiddenTerm() throws Exception {
        Field field = OpenAiSpeechStyleAnalyzer.class.getDeclaredField("FORBIDDEN");
        field.setAccessible(true);
        return ((Set<String>) field.get(null)).iterator().next();
    }

    @SuppressWarnings("unchecked")
    private String safeProfanityTerm() throws Exception {
        Field field = OpenAiSpeechStyleAnalyzer.class.getDeclaredField("SAFE_SELF_DIRECTED_PROFANITY");
        field.setAccessible(true);
        return ((Set<String>) field.get(null)).iterator().next();
    }

    private PreprocessedSpeechData data(List<PreprocessedSpeechMessage> messages) {
        return data(messages, Map.of());
    }

    private PreprocessedSpeechData data(List<PreprocessedSpeechMessage> messages, Map<String, Integer> profanity) {
        return new PreprocessedSpeechData(
                messages,
                50,
                Map.of("hello", 3),
                profanity
        );
    }

    private PreprocessedSpeechMessage message(String id, String content) {
        return new PreprocessedSpeechMessage(id, "[PERSON] context", content);
    }

    private String candidate(String id, String category) {
        return "{\"messageId\":\"%s\",\"category\":\"%s\"}".formatted(id, category);
    }

    private JsonNode analysisResponse(List<String> candidates) {
        return objectMapper.readTree("""
                {
                  "profile":{
                    "speechLevel":"BANMAL","sentenceLength":"SHORT","directness":"MEDIUM",
                    "warmth":"MEDIUM","playfulness":"LOW","emotionalIntensity":"MEDIUM",
                    "openingPatterns":["hello"],"endingPatterns":["bye"],
                    "reactionPatterns":["good"],"avoidPatterns":["too much"],
                    "punctuationStyle":{"period":"LOW","questionMark":"MEDIUM",
                      "exclamationMark":"LOW","repetition":"LOW"}
                  },
                  "candidates":[%s]
                }
                """.formatted(String.join(",", candidates)));
    }

    private static final class QueueGateway implements OpenAiGateway {
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private final List<String> schemaNames = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public JsonNode structuredResponse(
                String schemaName,
                String promptVersion,
                String instructions,
                String inputText,
                List<OpenAiImageInput> images,
                JsonNode schema,
                int maxOutputTokens
        ) {
            schemaNames.add(schemaName);
            return responses.remove();
        }

        @Override
        public byte[] editImage(
                String promptVersion,
                String prompt,
                List<OpenAiImageInput> images,
                OpenAiImageInput mask,
                String size,
                String quality
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
