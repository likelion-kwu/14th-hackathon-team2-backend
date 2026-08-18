package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.JsonNode;

class AvatarAssetTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesAllDefaultAssetsAsExactRgbaPngAndRejectsTraversal() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(
                new AvatarProperties(temporaryDirectory),
                processor,
                templates
        );

        storage.initializeDefaults();

        for (AvatarGrowthTrack track : AvatarGrowthTrack.values()) {
            for (int stage = 1; stage <= 3; stage++) {
                assertThat(storage.stageResource(storage.defaultKey(track), stage)).isNotNull();
                assertThat(storage.stageResource(storage.defaultKey(track), stage).exists()).isTrue();
            }
        }
        byte[] sample;
        try (var input = storage.stageResource(storage.defaultKey(AvatarGrowthTrack.SKIN), 1).getInputStream()) {
            sample = input.readAllBytes();
        }
        BufferedImage decoded = processor.decode(sample);
        long transparent = 0;
        long opaque = 0;
        for (int y = 0; y < decoded.getHeight(); y++) {
            for (int x = 0; x < decoded.getWidth(); x++) {
                int alpha = (decoded.getRGB(x, y) >>> 24) & 0xff;
                transparent += alpha == 0 ? 1 : 0;
                opaque += alpha == 255 ? 1 : 0;
            }
        }
        assertThat(transparent).isPositive();
        assertThat(opaque).isPositive();
        assertThatThrownBy(() -> storage.resolve("../../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesExistingAlphaWhileRemovingConnectedLightBackground() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        BufferedImage source = new BufferedImage(20, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(new Color(255, 255, 255, 255));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.setComposite(java.awt.AlphaComposite.Src);
        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.fillRect(4, 8, 12, 24);
        graphics.setColor(new Color(80, 120, 160, 128));
        graphics.fillRect(8, 12, 4, 16);
        graphics.dispose();

        BufferedImage prepared = processor.prepareTemplate(png(source));

        assertThat((prepared.getRGB(0, 0) >>> 24) & 0xff).isZero();
        assertThat((prepared.getRGB(320, 640) >>> 24) & 0xff).isBetween(120, 136);
    }

    @Test
    void stageTwoAndThreeBothUseStageOneInsteadOfChaining() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        byte[] stageOneRaw = image(Color.ORANGE);
        CapturingGateway gateway = new CapturingGateway(stageOneRaw, image(Color.YELLOW));
        OpenAiAvatarSetGenerator generator = new OpenAiAvatarSetGenerator(gateway, templates, processor);

        try {
            List<byte[]> result = generator.generate(AvatarGrowthTrack.SKIN, null);

            assertThat(result).hasSize(3).allSatisfy(asset -> assertThat(processor.isValidFinalPng(asset)).isTrue());
            assertThat(gateway.inputs).hasSize(3);
            assertThat(gateway.inputs.subList(1, 3))
                    .allSatisfy(input -> assertThat(Arrays.equals(input, stageOneRaw)).isTrue());
        } finally {
            generator.shutdown();
        }
    }

    private byte[] image(Color color) throws Exception {
        BufferedImage image = new BufferedImage(640, 1280, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static final class CapturingGateway implements OpenAiGateway {
        private final byte[] stageOne;
        private final byte[] laterStage;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<byte[]> inputs = new CopyOnWriteArrayList<>();

        private CapturingGateway(byte[] stageOne, byte[] laterStage) {
            this.stageOne = stageOne;
            this.laterStage = laterStage;
        }

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
            inputs.add(images.get(0).bytes());
            return calls.incrementAndGet() == 1 ? stageOne : laterStage;
        }
    }
}
