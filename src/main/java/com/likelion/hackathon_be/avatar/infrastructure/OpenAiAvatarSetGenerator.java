package com.likelion.hackathon_be.avatar.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAvatarSetGenerator {
    private static final String PROMPT_VERSION = "avatar-generation-v5";
    private static final String SIZE = AvatarImageProcessor.WORK_WIDTH + "x" + AvatarImageProcessor.WORK_HEIGHT;
    private static final String QUALITY = "medium";

    private final OpenAiGateway gateway;
    private final AvatarTemplateAssets templateAssets;
    private final AvatarImageProcessor imageProcessor;
    private final ExecutorService stageExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "avatar-stage-generator");
        thread.setDaemon(true);
        return thread;
    });

    public OpenAiAvatarSetGenerator(
            OpenAiGateway gateway,
            AvatarTemplateAssets templateAssets,
            AvatarImageProcessor imageProcessor
    ) {
        this.gateway = gateway;
        this.templateAssets = templateAssets;
        this.imageProcessor = imageProcessor;
    }

    public List<byte[]> generate(AvatarGrowthTrack track, AvatarFaceReference faceReference) {
        if (!gateway.isAvailable()) {
            throw new AvatarGenerationException("OpenAI image generation is not configured");
        }
        byte[] template = templateAssets.templatePng();
        byte[] identityMask = templateAssets.identityMaskPng();
        byte[] faceEvolutionMask = templateAssets.faceEvolutionMaskPng();
        List<OpenAiImageInput> stageOneInputs = new ArrayList<>();
        stageOneInputs.add(new OpenAiImageInput(template, "image/png"));
        if (faceReference != null) {
            stageOneInputs.add(new OpenAiImageInput(faceReference.bytes(), faceReference.mediaType()));
        }

        GeneratedStage stageOne = generateWithRetry(
                stagePrompt(track, 1, faceReference != null),
                stageOneInputs,
                identityMask,
                templateAssets.template(),
                1.0d
        );
        CompletableFuture<GeneratedStage> stageTwo = CompletableFuture.supplyAsync(
                () -> generateWithRetry(
                        stagePrompt(track, 2, false),
                        List.of(new OpenAiImageInput(stageOne.composed(), "image/png")),
                        faceEvolutionMask,
                        imageProcessor.decode(stageOne.composed()),
                        evolutionStrength(2)
                ),
                stageExecutor
        );
        CompletableFuture<GeneratedStage> stageThree = CompletableFuture.supplyAsync(
                () -> generateWithRetry(
                        stagePrompt(track, 3, false),
                        List.of(new OpenAiImageInput(stageOne.composed(), "image/png")),
                        faceEvolutionMask,
                        imageProcessor.decode(stageOne.composed()),
                        evolutionStrength(3)
                ),
                stageExecutor
        );

        try {
            CompletableFuture.allOf(stageTwo, stageThree).join();
            return List.of(
                    imageProcessor.toFinalPng(stageOne.composed()),
                    imageProcessor.toFinalPng(stageTwo.join().composed()),
                    imageProcessor.toFinalPng(stageThree.join().composed())
            );
        } catch (CompletionException exception) {
            throw new AvatarGenerationException("Avatar stage generation failed", exception.getCause());
        } catch (RuntimeException exception) {
            throw new AvatarGenerationException("Avatar normalization failed", exception);
        }
    }

    @PreDestroy
    void shutdown() {
        stageExecutor.shutdownNow();
    }

    private GeneratedStage generateWithRetry(
            String prompt,
            List<OpenAiImageInput> inputs,
            byte[] mask,
            java.awt.image.BufferedImage base,
            double editStrength
    ) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                byte[] generated = gateway.editImage(
                        PROMPT_VERSION,
                        prompt,
                        inputs,
                        new OpenAiImageInput(mask, "image/png"),
                        SIZE,
                        QUALITY
                );
                byte[] composed = imageProcessor.composeMaskedEdit(
                        generated,
                        base,
                        imageProcessor.decode(mask),
                        editStrength
                );
                return new GeneratedStage(composed);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (exception instanceof OpenAiGatewayException gatewayException
                        && gatewayException.kind() != OpenAiGatewayException.Kind.INVALID_RESPONSE) {
                    break;
                }
            }
        }
        throw new AvatarGenerationException("Image edit failed after one retry", lastFailure);
    }

    private record GeneratedStage(byte[] composed) {
    }

    private double evolutionStrength(int stage) {
        return stage == 2 ? 0.08d : 0.15d;
    }

    private String stagePrompt(AvatarGrowthTrack track, int stage, boolean hasFaceReference) {
        String personalization;
        if (stage > 1) {
            personalization = "Keep the exact face identity, hairstyle, hair color, skin tone, and facial geometry "
                    + "from the first image. Do not redesign the person. ";
        } else if (hasFaceReference) {
            personalization = "The second image is a face-only identity reference. Translate its general face "
                    + "shape, skin tone, hairstyle, hair color, eyebrows, eye shape and spacing, nose, and mouth "
                    + "into simplified game-avatar geometry. Never copy its body, clothing, pose, background, "
                    + "camera, lighting, eyewear, jewelry, accessories, or photographic skin texture. Even if "
                    + "the person wears glasses, the avatar must have no glasses. ";
        } else {
            personalization = "Keep the existing neutral template face identity and hairstyle. ";
        }
        return """
                The first image is the authoritative canonical avatar template. Edit only the transparent
                identity-and-hair mask region of that first image and preserve every pixel outside the mask.
                Keep its exact full-body composition, compact oversized-head proportions, upright front-facing
                pose, body silhouette, white short-sleeve T-shirt, black shorts, white socks without shoes,
                camera, framing, scale, lighting, and position. Never change the body because of the person.

                Render a polished standardized 3D Mii-inspired game avatar: smooth rounded geometry, clean oval
                eyes, simplified eyebrows, small stylized nose, simple friendly smile, matte-to-semi-matte
                surfaces, soft studio lighting, and no realistic pores or photographic detail. Do not produce
                anime, photorealism, a realistic 3D human, text, logos, accessories, props, or scenery.

                %s
                Apply only this fictional visual growth preset, never a diagnosis or health prediction: %s.
                %s
                This is stage %d of 3. Body size and proportions must remain identical across every stage.
                Use a uniform exact #E5F7FF matte wherever a temporary background is needed; no gradient.
                Return one centered full-body avatar on the same 1:2 canvas.
                """.formatted(personalization, preset(track, stage), evolutionConstraints(track, stage), stage);
    }

    private String evolutionConstraints(AvatarGrowthTrack track, int stage) {
        if (stage == 1) {
            return "Do not add glasses or any other accessory.";
        }
        return switch (track) {
            case SKIN -> "Change only the subtle stylized facial surface finish. Preserve the exact hairstyle, "
                    + "hairline, face silhouette, eyes, eyebrows, nose, mouth, ears, and apparent identity.";
            case WELL_BEING -> "Change only subtle complexion brightness and saturation. Preserve the exact "
                    + "hairstyle, hairline, face silhouette, eyes, eyebrows, nose, mouth, ears, and identity.";
            case HEALTH_FIT, DIET -> "Change only the outer cheek and jaw contour slightly. Preserve the exact "
                    + "hairstyle, hairline, eyes, eyebrows, nose, mouth, ears, skin tone, and identity.";
        };
    }

    private String preset(AvatarGrowthTrack track, int stage) {
        return switch (track) {
            case SKIN -> switch (stage) {
                case 1 -> "slightly less visually tidy facial texture";
                case 2 -> "moderately tidy and even facial texture";
                default -> "cleaner and more even stylized facial texture";
            };
            case WELL_BEING -> switch (stage) {
                case 1 -> "slightly muted and tired fictional complexion";
                case 2 -> "neutral balanced fictional complexion";
                default -> "brighter and lively fictional complexion";
            };
            case HEALTH_FIT, DIET -> switch (stage) {
                case 1 -> "slightly rounder stylized facial outline only";
                case 2 -> "neutral stylized facial outline";
                default -> "slightly more defined stylized facial outline only";
            };
        };
    }
}
