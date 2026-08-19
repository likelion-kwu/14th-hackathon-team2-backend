package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
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
    void initializationRemovesOnlyStaleTemporarySetsAndRebuildsDefaults() throws Exception {
        Path staleTemporarySet = temporaryDirectory.resolve(".tmp/stale-set");
        Path persistentGeneratedSet = temporaryDirectory.resolve("generated/101/existing-set");
        Files.createDirectories(staleTemporarySet);
        Files.createDirectories(persistentGeneratedSet);
        Files.writeString(staleTemporarySet.resolve("partial.png"), "incomplete");
        Files.writeString(persistentGeneratedSet.resolve("marker"), "keep");

        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(new AvatarProperties(temporaryDirectory), processor, templates);

        storage.initializeDefaults();

        assertThat(staleTemporarySet).doesNotExist();
        assertThat(temporaryDirectory.resolve(".tmp")).isDirectory();
        try (var temporaryEntries = Files.list(temporaryDirectory.resolve(".tmp"))) {
            assertThat(temporaryEntries.findAny()).isEmpty();
        }
        assertThat(persistentGeneratedSet.resolve("marker")).hasContent("keep");
        for (AvatarGrowthTrack track : AvatarGrowthTrack.values()) {
            for (int stage = 1; stage <= 3; stage++) {
                assertThat(storage.stageResource(storage.defaultKey(track), stage)).isNotNull();
            }
        }
    }

    @Test
    void startupRecoveryDeletesOnlyCompleteUnreferencedGeneratedSets() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(new AvatarProperties(temporaryDirectory), processor, templates);
        storage.initializeDefaults();
        List<byte[]> validStages = List.of(
                processor.createDefaultStage(templates.template(), AvatarGrowthTrack.SKIN, 1),
                processor.createDefaultStage(templates.template(), AvatarGrowthTrack.SKIN, 2),
                processor.createDefaultStage(templates.template(), AvatarGrowthTrack.SKIN, 3)
        );
        String referencedKey = storage.storeGenerated(101L, validStages);
        String orphanedKey = storage.storeGenerated(101L, validStages);

        Path malformed = temporaryDirectory.resolve("generated/101/" + UUID.randomUUID());
        Files.createDirectories(malformed);
        Files.write(malformed.resolve("stage1.png"), validStages.get(0));
        Path unknown = temporaryDirectory.resolve("generated/not-a-user/unknown-directory");
        Files.createDirectories(unknown);
        Files.writeString(unknown.resolve("marker"), "keep");

        Path outside = Files.createTempDirectory("avatar-recovery-outside-");
        for (int stage = 1; stage <= 3; stage++) {
            Files.write(outside.resolve("stage" + stage + ".png"), validStages.get(stage - 1));
        }
        Path symlink = temporaryDirectory.resolve("generated/102/" + UUID.randomUUID());
        Files.createDirectories(symlink.getParent());
        Files.createSymbolicLink(symlink, outside);

        try {
            int deleted = storage.cleanupUnreferencedGeneratedSets(Set.of(referencedKey + "/"));

            assertThat(deleted).isEqualTo(1);
            assertThat(temporaryDirectory.resolve(referencedKey)).isDirectory();
            assertThat(temporaryDirectory.resolve(orphanedKey)).doesNotExist();
            assertThat(malformed).isDirectory();
            assertThat(unknown).isDirectory();
            assertThat(Files.isSymbolicLink(symlink)).isTrue();
            assertThat(outside.resolve("stage1.png")).exists();
            assertThat(storage.stageResource(storage.defaultKey(AvatarGrowthTrack.SKIN), 1)).isNotNull();
        } finally {
            for (int stage = 1; stage <= 3; stage++) {
                Files.deleteIfExists(outside.resolve("stage" + stage + ".png"));
            }
            Files.deleteIfExists(outside);
        }
    }

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
    void healthAndDietDefaultStagesChangeOnlyTheHumanFacePresetArea() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();

        for (AvatarGrowthTrack track : List.of(AvatarGrowthTrack.HEALTH_FIT, AvatarGrowthTrack.DIET)) {
            BufferedImage stageOne = processor.decode(processor.createDefaultStage(templates.template(), track, 1));
            BufferedImage stageThree = processor.decode(processor.createDefaultStage(templates.template(), track, 3));

            int changedFacePixels = 0;
            for (int y = 20; y < 145; y++) {
                for (int x = 75; x < 175; x++) {
                    if (stageOne.getRGB(x, y) != stageThree.getRGB(x, y)) {
                        changedFacePixels++;
                    }
                }
            }
            assertThat(changedFacePixels).isGreaterThan(500);
            for (int y = 160; y < stageOne.getHeight(); y++) {
                for (int x = 0; x < stageOne.getWidth(); x++) {
                    assertThat(stageOne.getRGB(x, y)).isEqualTo(stageThree.getRGB(x, y));
                }
            }
        }
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
            assertThat(gateway.inputs.get(1)).isEqualTo(gateway.inputs.get(2));
            assertThat(gateway.inputs.get(1)).isNotEqualTo(stageOneRaw);
            assertThat(gateway.prompts.subList(1, 3))
                    .allSatisfy(prompt -> assertThat(prompt).contains("exact face identity"));
        } finally {
            generator.shutdown();
        }
    }

    @Test
    void compositesOnlyTheTransparentMaskRegionAndRejectsOpaqueOutput() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        BufferedImage base = templates.template();
        byte[] edited = image(Color.MAGENTA);

        byte[] composedBytes = processor.composeMaskedEdit(
                edited,
                base,
                processor.decode(templates.faceMaskPng())
        );
        BufferedImage composed = processor.decode(composedBytes);

        assertThat(composed.getRGB(320, 700)).isEqualTo(base.getRGB(320, 700));
        assertThat(composed.getRGB(320, 150) & 0x00ffffff).isEqualTo(Color.MAGENTA.getRGB() & 0x00ffffff);
        assertThat(processor.isValidFinalPng(processor.toFinalPng(composedBytes))).isTrue();
        assertThat(processor.isValidFinalPng(processor.toFinalPng(image(Color.BLACK)))).isFalse();
    }

    @Test
    void deleteGeneratedCannotTraverseIntoDefaultsAndSymlinkReadsCannotEscape() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(new AvatarProperties(temporaryDirectory), processor, templates);
        storage.initializeDefaults();

        storage.deleteGenerated("generated/../defaults/skin");
        assertThat(storage.stageResource(storage.defaultKey(AvatarGrowthTrack.SKIN), 1)).isNotNull();

        Path outside = Files.createTempDirectory("avatar-outside-");
        try {
            Files.write(outside.resolve("stage1.png"), processor.createDefaultStage(
                    templates.template(), AvatarGrowthTrack.SKIN, 1
            ));
            Files.createSymbolicLink(temporaryDirectory.resolve("link"), outside);
            assertThatThrownBy(() -> storage.stageResource("link", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(outside.resolve("stage1.png"));
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void providerUnavailabilityIsNotRetriedAgainAboveGateway() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AtomicInteger calls = new AtomicInteger();
        OpenAiGateway unavailable = new OpenAiGateway() {
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
                calls.incrementAndGet();
                throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "down");
            }
        };
        OpenAiAvatarSetGenerator generator = new OpenAiAvatarSetGenerator(unavailable, templates, processor);

        try {
            assertThatThrownBy(() -> generator.generate(AvatarGrowthTrack.SKIN, null))
                    .isInstanceOf(AvatarGenerationException.class);
            assertThat(calls).hasValue(1);
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
        private final List<String> prompts = new CopyOnWriteArrayList<>();

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
            prompts.add(prompt);
            return calls.incrementAndGet() == 1 ? stageOne : laterStage;
        }
    }
}
