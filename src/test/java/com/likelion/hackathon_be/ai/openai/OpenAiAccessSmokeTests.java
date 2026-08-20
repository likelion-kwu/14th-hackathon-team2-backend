package com.likelion.hackathon_be.ai.openai;

import java.time.Duration;
import java.util.List;

import com.likelion.hackathon_be.avatar.infrastructure.AvatarImageProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_SMOKE", matches = "(?i)true")
class OpenAiAccessSmokeTests {
    @Test
    void verifiesResponsesAndImageEditModelAccessWithNonPersonalAssets() throws Exception {
        String apiKey = requiredEnvironment("OPENAI_API_KEY");
        OpenAiProperties properties = new OpenAiProperties(
                apiKey,
                System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com"),
                System.getenv().getOrDefault("OPENAI_RESPONSES_MODEL", "gpt-5.6-luna"),
                System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-2"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofSeconds(130)
        );
        ObjectMapper objectMapper = new ObjectMapper();
        RestOpenAiGateway gateway = new RestOpenAiGateway(properties, objectMapper);
        JsonNode result = gateway.structuredResponse(
                "smoke_result",
                "smoke-v1",
                "Return only the strict schema. Do not infer anything about people.",
                "Return ok=true.",
                List.of(),
                objectMapper.readTree("""
                        {"type":"object","additionalProperties":false,
                         "properties":{"ok":{"type":"boolean","const":true}},"required":["ok"]}
                        """),
                50
        );
        assertThat(result.path("ok").booleanValue()).isTrue();

        AvatarImageProcessor processor = new AvatarImageProcessor();
        byte[] source;
        try (var input = new ClassPathResource("avatar/canonical-human-base.png").getInputStream()) {
            source = input.readAllBytes();
        }
        byte[] template = processor.encodePng(processor.prepareTemplate(source));
        byte[] mask = processor.encodePng(processor.createIdentityMask());
        JsonNode vision = gateway.structuredResponse(
                "vision_smoke_result",
                "smoke-v1",
                "Confirm only whether an image input was received. Return the strict schema.",
                "Inspect the attached non-personal avatar template.",
                List.of(new OpenAiImageInput(template, "image/png", "high")),
                objectMapper.readTree("""
                        {"type":"object","additionalProperties":false,
                         "properties":{"imageReceived":{"type":"boolean","const":true}},
                         "required":["imageReceived"]}
                        """),
                50
        );
        assertThat(vision.path("imageReceived").booleanValue()).isTrue();

        byte[] generated = gateway.editImage(
                "smoke-v1",
                "Preserve this exact fictional 2D human avatar and make a tiny neutral color adjustment inside the mask.",
                List.of(new OpenAiImageInput(template, "image/png")),
                new OpenAiImageInput(mask, "image/png"),
                "640x1280",
                "low"
        );
        assertThat(processor.decode(generated)).isNotNull();
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_OPENAI_SMOKE=true");
        }
        return value;
    }
}
