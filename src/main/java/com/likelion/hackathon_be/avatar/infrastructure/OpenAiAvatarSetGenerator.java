package com.likelion.hackathon_be.avatar.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class OpenAiAvatarSetGenerator {
    private static final String PROMPT_VERSION = "avatar-generation-v2";
    private static final String SIZE = "640x1280";
    private static final String QUALITY = "low";

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
        byte[] mask = templateAssets.faceMaskPng();
        List<OpenAiImageInput> stageOneInputs = new ArrayList<>();
        stageOneInputs.add(new OpenAiImageInput(template, "image/png"));
        if (faceReference != null) {
            stageOneInputs.add(new OpenAiImageInput(faceReference.bytes(), faceReference.mediaType()));
        }

        byte[] stageOneRaw = generateWithRetry(stagePrompt(track, 1, faceReference != null), stageOneInputs, mask);
        CompletableFuture<byte[]> stageTwo = CompletableFuture.supplyAsync(
                () -> generateWithRetry(
                        stagePrompt(track, 2, false),
                        List.of(new OpenAiImageInput(stageOneRaw, "image/png")),
                        mask
                ),
                stageExecutor
        );
        CompletableFuture<byte[]> stageThree = CompletableFuture.supplyAsync(
                () -> generateWithRetry(
                        stagePrompt(track, 3, false),
                        List.of(new OpenAiImageInput(stageOneRaw, "image/png")),
                        mask
                ),
                stageExecutor
        );

        try {
            CompletableFuture.allOf(stageTwo, stageThree).join();
            return List.of(
                    imageProcessor.normalizeGenerated(stageOneRaw, templateAssets.template()),
                    imageProcessor.normalizeGenerated(stageTwo.join(), templateAssets.template()),
                    imageProcessor.normalizeGenerated(stageThree.join(), templateAssets.template())
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

    private byte[] generateWithRetry(String prompt, List<OpenAiImageInput> inputs, byte[] mask) {
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
                imageProcessor.decode(generated);
                return generated;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw new AvatarGenerationException("Image edit failed after one retry", lastFailure);
    }

    private String stagePrompt(AvatarGrowthTrack track, int stage, boolean hasFaceReference) {
        String personalization = hasFaceReference
                ? "Use the second image only as a subtle reference for face shape, eyes, and overall facial impression. "
                : "Keep the existing neutral template face identity. ";
        return """
                Edit only the transparent face-mask region of the first image. Preserve the exact 2D flat human
                avatar, canvas, full-body pose, body proportions, clothing, outline, body position, and all
                pixels outside the mask. Do not add accessories or text. Never change body shape. %s
                Apply this fictional game preset, not a diagnosis or prediction: %s. This is stage %d of 3.
                Keep the same human identity across stages and return a single centered full-body human avatar.
                """.formatted(personalization, preset(track, stage), stage);
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
