package com.likelion.hackathon_be.speech.application;

import java.util.List;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SafeDialogueCatalogTests {
    @Test
    void unavailableAiStillReturnsEightByFiveSafeCalmDialogues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SafeDialogueCatalog catalog = new SafeDialogueCatalog(objectMapper);
        catalog.load();
        DialogueCandidateGenerator generator = new DialogueCandidateGenerator(
                new UnavailableGateway(),
                objectMapper,
                catalog
        );
        SpeechProfileCandidate profile = new SpeechProfileCandidate(
                SpeechSourceType.PRESET,
                "CALM",
                SpeechStyleSettings.calm(),
                "{}",
                false,
                null,
                List.of()
        );

        List<DialogueCandidate> result = generator.generate(profile, "사자", java.util.Set.of());

        assertThat(result).hasSize(40);
        for (DialogueSituation situation : DialogueSituation.values()) {
            assertThat(result.stream().filter(candidate -> candidate.situation() == situation)).hasSize(5);
        }
        assertThat(result).allSatisfy(candidate -> assertThat(candidate.content().length()).isLessThanOrEqualTo(50));
    }

    private static final class UnavailableGateway implements OpenAiGateway {
        @Override
        public boolean isAvailable() {
            return false;
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
            throw new UnsupportedOperationException();
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
