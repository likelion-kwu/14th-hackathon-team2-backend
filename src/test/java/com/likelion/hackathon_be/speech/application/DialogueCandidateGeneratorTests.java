package com.likelion.hackathon_be.speech.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialogueCandidateGeneratorTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesSafeCatalogOnlyForExplicitFallbackAndStrictModePreservesCurrentProfile() throws Exception {
        SafeDialogueCatalog catalog = catalog();
        DialogueCandidateGenerator generator = new DialogueCandidateGenerator(
                new UnavailableGateway(), objectMapper, catalog
        );

        assertThat(generator.generateWithSafeFallback(profile(false), "사용자", Set.of())).hasSize(40);
        assertThatThrownBy(() -> generator.generateStrict(profile(false), "사용자", Set.of()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DIALOGUE_GENERATION_FAILED));
    }

    @Test
    void profanityRemainsBlockedWhenDetectedButUserSettingIsOff() throws Exception {
        SafeDialogueCatalog catalog = catalog();
        QueueGateway gateway = new QueueGateway();
        JsonNode batch = safeBatch();
        ((tools.jackson.databind.node.ArrayNode) batch.path("dialogues").get(1).path("lines"))
                .set(0, objectMapper.valueToTree("지금 시발 하나 해보자."));
        gateway.responses.add(batch);
        DialogueCandidateGenerator generator = new DialogueCandidateGenerator(gateway, objectMapper, catalog);

        List<DialogueCandidate> result = generator.generateStrict(profile(false), "사용자", Set.of("시발"));

        assertThat(result).hasSize(40).noneMatch(candidate -> candidate.content().contains("시발"));
        assertThat(result).noneMatch(DialogueCandidate::containsProfanity);
    }

    @Test
    void retriesOneInvalidStructuredBatch() throws Exception {
        SafeDialogueCatalog catalog = catalog();
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(new OpenAiGatewayException(OpenAiGatewayException.Kind.INVALID_RESPONSE, "bad json"));
        gateway.responses.add(safeBatch());
        DialogueCandidateGenerator generator = new DialogueCandidateGenerator(gateway, objectMapper, catalog);

        List<DialogueCandidate> result = generator.generateStrict(profile(false), "사용자", Set.of());

        assertThat(result).hasSize(40);
        assertThat(gateway.calls).isEqualTo(2);
    }

    @Test
    void retriesJsonThatViolatesExactBatchShapeBeforeActivatingFallbackLines() throws Exception {
        SafeDialogueCatalog catalog = catalog();
        QueueGateway gateway = new QueueGateway();
        gateway.responses.add(objectMapper.readTree("{\"dialogues\":[]}"));
        gateway.responses.add(safeBatch());
        DialogueCandidateGenerator generator = new DialogueCandidateGenerator(gateway, objectMapper, catalog);

        List<DialogueCandidate> result = generator.generateStrict(profile(false), "사용자", Set.of());

        assertThat(result).hasSize(40);
        assertThat(gateway.calls).isEqualTo(2);
    }

    @Test
    void activatorRejectsWrongSituationDistributionBeforeOpeningTransaction() throws Exception {
        SafeDialogueCatalog catalog = catalog();
        List<DialogueCandidate> invalid = new ArrayList<>(catalog.all());
        DialogueCandidate first = invalid.get(0);
        invalid.set(0, new DialogueCandidate(
                DialogueSituation.ROUTINE_AVAILABLE,
                first.content(),
                false,
                false
        ));
        SpeechProfileActivator activator = new SpeechProfileActivator(null, null, null, null, null);

        assertThatThrownBy(() -> activator.validateCandidate(profile(false), invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SpeechProfileCandidate profile(boolean profanityEnabled) {
        SpeechStyleSettings calm = SpeechStyleSettings.calm();
        SpeechStyleSettings settings = new SpeechStyleSettings(
                calm.speechLevel(),
                calm.sentenceLength(),
                calm.directness(),
                calm.warmth(),
                calm.playfulness(),
                calm.emotionalIntensity(),
                profanityEnabled
        );
        return new SpeechProfileCandidate(
                SpeechSourceType.PRESET,
                "CALM",
                settings,
                "{}",
                true,
                null,
                List.of()
        );
    }

    private SafeDialogueCatalog catalog() throws Exception {
        SafeDialogueCatalog catalog = new SafeDialogueCatalog(objectMapper);
        catalog.load();
        return catalog;
    }

    private JsonNode safeBatch() {
        Map<DialogueSituation, String> bases = Map.of(
                DialogueSituation.ROUTINE_UPCOMING, "곧 시작을 준비하자",
                DialogueSituation.ROUTINE_AVAILABLE, "지금 시작해보자",
                DialogueSituation.ROUTINE_REMINDER, "아직 지금 하나 해보자",
                DialogueSituation.ROUTINE_COMPLETED, "좋아 하나 끝냈어",
                DialogueSituation.ALL_COMPLETED, "오늘 전부 완료했어",
                DialogueSituation.STREAK_CONTINUED, "꾸준히 기록을 이어가자",
                DialogueSituation.STREAK_BROKEN, "괜찮아 다시 시작하자",
                DialogueSituation.RETURN_AFTER_ABSENCE, "다시 와서 반가워"
        );
        List<Map<String, Object>> groups = new ArrayList<>();
        for (DialogueSituation situation : DialogueSituation.values()) {
            List<String> lines = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                lines.add(bases.get(situation) + " " + index);
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("situation", situation.name());
            group.put("lines", lines);
            groups.add(group);
        }
        return objectMapper.valueToTree(Map.of("dialogues", groups));
    }

    private static class QueueGateway implements OpenAiGateway {
        private final Queue<Object> responses = new ArrayDeque<>();
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
            Object response = responses.remove();
            if (response instanceof RuntimeException exception) {
                throw exception;
            }
            return (JsonNode) response;
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

    private static final class UnavailableGateway extends QueueGateway {
        @Override
        public boolean isAvailable() {
            return false;
        }
    }
}
