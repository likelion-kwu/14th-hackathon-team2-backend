package com.likelion.hackathon_be.speech.infrastructure;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiSpeechStyleAnalyzerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void storesOnlyExtendedJsonAndCalculatesObservedProfanityFrequency() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse());
        gateway.responses.add(objectMapper.readTree("""
                {"examples":[{"category":"GENERAL","sourceType":"USER_MESSAGE","content":"근데 오늘은 해볼 만해"}]}
                """));
        OpenAiSpeechStyleAnalyzer analyzer = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper);

        AnalyzedSpeechProfile analyzed = analyzer.analyze(data(Map.of("시발", 6)));
        JsonNode style = objectMapper.readTree(analyzed.profile().styleJson());

        assertThat(style.has("speechLevel")).isFalse();
        assertThat(style.path("openingPatterns").get(0).asText()).isEqualTo("근데");
        assertThat(style.path("profanity").path("observedFrequency").asText()).isEqualTo("HIGH");
        assertThat(style.path("profanity").path("enabledByUser").booleanValue()).isFalse();
        assertThat(analyzed.allowedProfanity()).containsExactly("시발");
        assertThat(analyzed.profile().examples()).hasSize(1);
    }

    @Test
    void rejectsHallucinatedContentClaimedAsUserMessageAfterOneSchemaRetry() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse());
        JsonNode forged = objectMapper.readTree("""
                {"examples":[{"category":"GENERAL","sourceType":"USER_MESSAGE","content":"사용자가 말하지 않은 문장"}]}
                """);
        gateway.responses.add(forged);
        gateway.responses.add(forged);
        OpenAiSpeechStyleAnalyzer analyzer = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper);

        assertThatThrownBy(() -> analyzer.analyze(data(Map.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        assertThat(gateway.calls).isEqualTo(3);
    }

    @Test
    void rejectsAiGeneratedReplacementWhenThatCategoryHasARealCandidate() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse());
        JsonNode unnecessaryGenerated = objectMapper.readTree("""
                {"examples":[{"category":"GENERAL","sourceType":"AI_GENERATED","content":"오늘은 가볍게 해보자"}]}
                """);
        gateway.responses.add(unnecessaryGenerated);
        gateway.responses.add(unnecessaryGenerated);

        assertThatThrownBy(() -> new OpenAiSpeechStyleAnalyzer(gateway, objectMapper).analyze(data(Map.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AI_RESPONSE_INVALID));
        assertThat(gateway.calls).isEqualTo(3);
    }

    @Test
    void allowsAiGeneratedExampleOnlyForMissingCategory() throws Exception {
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(analysisResponse());
        gateway.responses.add(objectMapper.readTree("""
                {"examples":[
                  {"category":"GENERAL","sourceType":"USER_MESSAGE","content":"근데 오늘은 해볼 만해"},
                  {"category":"QUESTION","sourceType":"AI_GENERATED","content":"지금 하나 시작할까?"}
                ]}
                """));

        AnalyzedSpeechProfile analyzed = new OpenAiSpeechStyleAnalyzer(gateway, objectMapper).analyze(data(Map.of()));

        assertThat(analyzed.profile().examples()).hasSize(2);
        assertThat(gateway.calls).isEqualTo(2);
    }

    private PreprocessedSpeechData data(Map<String, Integer> profanity) {
        return new PreprocessedSpeechData(
                List.of(new PreprocessedSpeechMessage("m-001", "[PERSON] 오늘 할 거야?", "근데 오늘은 해볼 만해")),
                50,
                Map.of("근데", 3),
                profanity
        );
    }

    private JsonNode analysisResponse() {
        return objectMapper.readTree("""
                {
                  "profile":{
                    "speechLevel":"BANMAL","sentenceLength":"SHORT","directness":"MEDIUM",
                    "warmth":"MEDIUM","playfulness":"LOW","emotionalIntensity":"MEDIUM",
                    "openingPatterns":["근데"],"endingPatterns":["해볼 만해"],
                    "reactionPatterns":["좋아"],"avoidPatterns":["장황한 설명"],
                    "punctuationStyle":{"period":"LOW","questionMark":"MEDIUM",
                      "exclamationMark":"LOW","repetition":"LOW"}
                  },
                  "candidates":[{"messageId":"m-001","category":"GENERAL"}]
                }
                """);
    }

    private static final class QueueGateway implements OpenAiGateway {
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private int calls;

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
            calls++;
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
