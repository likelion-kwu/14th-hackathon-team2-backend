package com.likelion.hackathon_be.avatar.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.openai.OpenAiProperties;
import com.likelion.hackathon_be.ai.openai.RestOpenAiGateway;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_AVATAR_SMOKE", matches = "(?i)true")
class OpenAiAvatarGenerationSmokeTests {
    @TempDir
    Path storageRoot;

    @Test
    void generatesAllThreeStagesFromTheSampleFacePhoto() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenAiProperties properties = new OpenAiProperties(
                requiredEnvironment("OPENAI_API_KEY"),
                System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com"),
                System.getenv().getOrDefault("OPENAI_RESPONSES_MODEL", "gpt-5.6-luna"),
                System.getenv().getOrDefault("OPENAI_IMAGE_MODEL", "gpt-image-2"),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofSeconds(130)
        );
        RestOpenAiGateway gateway = new RestOpenAiGateway(properties, objectMapper);
        Path facePhotoPath = Path.of(requiredEnvironment("AVATAR_SMOKE_FACE_PHOTO"));
        byte[] facePhoto = Files.readAllBytes(facePhotoPath);
        AvatarFacePhotoValidator validator = new AvatarFacePhotoValidator(
                gateway,
                new ImageInputValidator(),
                new AvatarFaceReferenceNormalizer(),
                objectMapper
        );
        AvatarFaceReference faceReference = validator.validate(new MockMultipartFile(
                "facePhoto", facePhotoPath.getFileName().toString(), "image/png", facePhoto
        ));

        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        OpenAiAvatarSetGenerator generator = new OpenAiAvatarSetGenerator(gateway, templates, processor);
        try {
            List<byte[]> stages = generator.generate(AvatarGrowthTrack.WELL_BEING, faceReference);
            assertThat(stages).hasSize(3)
                    .allSatisfy(stage -> assertThat(processor.isValidFinalPng(stage)).isTrue());
            assertLockedLowerBodyAndVisibleFaceEvolution(processor, stages);

            AvatarStorage storage = new AvatarStorage(
                    new AvatarProperties(storageRoot),
                    processor,
                    templates
            );
            storage.initializeDefaults();
            String assetSetKey = storage.storeGenerated(101L, stages);
            for (int index = 0; index < stages.size(); index++) {
                var resource = storage.stageResource(assetSetKey, index + 1);
                assertThat(resource).isNotNull();
                try (var input = resource.getInputStream()) {
                    assertThat(input.readAllBytes()).containsExactly(stages.get(index));
                }
            }

            Path outputDirectory = Path.of("build/avatar-smoke");
            Files.createDirectories(outputDirectory);
            for (int index = 0; index < stages.size(); index++) {
                Files.write(outputDirectory.resolve("stage" + (index + 1) + ".png"), stages.get(index));
            }
        } finally {
            generator.shutdown();
        }
    }

    private void assertLockedLowerBodyAndVisibleFaceEvolution(
            AvatarImageProcessor processor,
            List<byte[]> stages
    ) {
        var stageOne = processor.decode(stages.get(0));
        for (int index = 1; index < stages.size(); index++) {
            var laterStage = processor.decode(stages.get(index));
            int faceDifferences = 0;
            int lowerBodyDifferences = 0;
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < AvatarImageProcessor.FINAL_WIDTH; x++) {
                    if (stageOne.getRGB(x, y) != laterStage.getRGB(x, y)) {
                        faceDifferences++;
                    }
                }
            }
            assertThat(faceDifferences).isGreaterThan(100);

            for (int y = 200; y < AvatarImageProcessor.FINAL_HEIGHT; y++) {
                for (int x = 0; x < AvatarImageProcessor.FINAL_WIDTH; x++) {
                    if (stageOne.getRGB(x, y) != laterStage.getRGB(x, y)) {
                        lowerBodyDifferences++;
                    }
                }
            }
            assertThat(lowerBodyDifferences).isZero();
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when RUN_OPENAI_AVATAR_SMOKE=true");
        }
        return value;
    }
}
