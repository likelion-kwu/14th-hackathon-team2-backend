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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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

        int deleted = storage.cleanupUnreferencedGeneratedSets(Set.of(referencedKey + "/"));

        assertThat(deleted).isEqualTo(1);
        assertThat(temporaryDirectory.resolve(referencedKey)).isDirectory();
        assertThat(temporaryDirectory.resolve(orphanedKey)).doesNotExist();
        assertThat(malformed).isDirectory();
        assertThat(unknown).isDirectory();
        assertThat(storage.stageResource(storage.defaultKey(AvatarGrowthTrack.SKIN), 1)).isNotNull();
    }

    @Test
    void startupRecoveryDoesNotFollowGeneratedSymlinkOutsideRoot() throws Exception {
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

        Path outside = Files.createTempDirectory("avatar-recovery-outside-");
        for (int stage = 1; stage <= 3; stage++) {
            Files.write(outside.resolve("stage" + stage + ".png"), validStages.get(stage - 1));
        }
        Path symlink = temporaryDirectory.resolve("generated/102/" + UUID.randomUUID());
        Files.createDirectories(symlink.getParent());
        createSymbolicLinkOrSkip(symlink, outside);

        try {
            int deleted = storage.cleanupUnreferencedGeneratedSets(Set.of());

            assertThat(deleted).isZero();
            assertThat(Files.isSymbolicLink(symlink)).isTrue();
            assertThat(outside.resolve("stage1.png")).exists();
        } finally {
            Files.deleteIfExists(symlink);
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
    void canonicalMiiTemplateRemovesItsBlueMatteWithoutErasingTheWhiteShirt() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        BufferedImage template = templates.template();

        assertThat(template.getRGB(0, 0)).isZero();
        assertThat((template.getRGB(320, 640) >>> 24) & 0xff).isEqualTo(255);
        assertThat((template.getRGB(320, 1_200) >>> 24) & 0xff).isZero();
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

        assertThat(prepared.getRGB(0, 0)).isZero();
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
                    .allSatisfy(prompt -> assertThat(prompt)
                            .contains("exact face identity")
                            .contains("Body size and proportions must remain identical"));
            assertThat(gateway.masks.get(0)).isNotEqualTo(gateway.masks.get(1));
            assertThat(gateway.masks.get(1)).isEqualTo(gateway.masks.get(2));
            assertThat(gateway.sizes).containsOnly("640x1280");
            assertThat(gateway.qualities).containsOnly("medium");
        } finally {
            generator.shutdown();
        }
    }

    @Test
    void stageOneUsesThePhotoOnlyAsAnIdentityReference() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        CapturingGateway gateway = new CapturingGateway(image(Color.ORANGE), image(Color.YELLOW));
        OpenAiAvatarSetGenerator generator = new OpenAiAvatarSetGenerator(gateway, templates, processor);

        try {
            generator.generate(
                    AvatarGrowthTrack.WELL_BEING,
                    new AvatarFaceReference(new byte[]{1, 2, 3}, "image/png")
            );

            assertThat(gateway.inputCounts).containsExactly(2, 1, 1);
            assertThat(gateway.prompts.get(0))
                    .contains("face-only identity reference")
                    .contains("Never copy its body, clothing, pose, background")
                    .contains("the avatar must have no glasses")
                    .contains("exact #E5F7FF matte")
                    .contains("stage 1 of 3");
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
                processor.decode(templates.identityMaskPng())
        );
        BufferedImage composed = processor.decode(composedBytes);

        assertThat(composed.getRGB(320, 700)).isEqualTo(base.getRGB(320, 700));
        assertThat(composed.getRGB(320, 150) & 0x00ffffff).isEqualTo(Color.MAGENTA.getRGB() & 0x00ffffff);
        assertThat(processor.isValidFinalPng(processor.toFinalPng(composedBytes))).isTrue();
        assertThat(processor.isValidFinalPng(processor.toFinalPng(image(Color.BLACK)))).isFalse();
    }

    @Test
    void finalAssetValidationRejectsEmptyOrTinyForeground() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        BufferedImage empty = new BufferedImage(
                AvatarImageProcessor.FINAL_WIDTH,
                AvatarImageProcessor.FINAL_HEIGHT,
                BufferedImage.TYPE_INT_ARGB
        );
        BufferedImage tiny = new BufferedImage(
                AvatarImageProcessor.FINAL_WIDTH,
                AvatarImageProcessor.FINAL_HEIGHT,
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = tiny.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillOval(100, 30, 50, 50);
        graphics.dispose();

        assertThat(processor.isValidFinalPng(processor.encodePng(empty))).isFalse();
        assertThat(processor.isValidFinalPng(processor.encodePng(tiny))).isFalse();
        assertThat(processor.isValidFinalPng(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a}))
                .isFalse();
    }

    @Test
    void partialEditStrengthKeepsStageOneAsTheDominantFaceReference() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        BufferedImage base = templates.template();
        BufferedImage mask = processor.createFaceEvolutionMask();
        byte[] edited = image(Color.MAGENTA);

        BufferedImage full = processor.decode(processor.composeMaskedEdit(edited, base, mask, 1.0d));
        BufferedImage partial = processor.decode(processor.composeMaskedEdit(edited, base, mask, 0.08d));
        int baseRed = (base.getRGB(320, 250) >>> 16) & 0xff;
        int fullRed = (full.getRGB(320, 250) >>> 16) & 0xff;
        int partialRed = (partial.getRGB(320, 250) >>> 16) & 0xff;

        assertThat(Math.abs(partialRed - baseRed)).isLessThan(Math.abs(fullRed - baseRed));
        assertThatThrownBy(() -> processor.composeMaskedEdit(edited, base, mask, 1.01d))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identityMaskIncludesFaceAndLongHairButKeepsTheLowerBodyLocked() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        BufferedImage mask = processor.createIdentityMask();

        assertThat((mask.getRGB(320, 150) >>> 24) & 0xff).isZero();
        assertThat((mask.getRGB(170, 500) >>> 24) & 0xff).isZero();
        assertThat((mask.getRGB(470, 500) >>> 24) & 0xff).isZero();
        assertThat((mask.getRGB(320, 800) >>> 24) & 0xff).isEqualTo(255);
    }

    @Test
    void evolutionMaskKeepsHairAndBodyLocked() {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        BufferedImage mask = processor.createFaceEvolutionMask();

        assertThat((mask.getRGB(320, 250) >>> 24) & 0xff).isZero();
        assertThat((mask.getRGB(170, 500) >>> 24) & 0xff).isEqualTo(255);
        assertThat((mask.getRGB(470, 500) >>> 24) & 0xff).isEqualTo(255);
        assertThat((mask.getRGB(320, 800) >>> 24) & 0xff).isEqualTo(255);
    }

    @Test
    void removesPaleSkyBlueMatteAndPreservesWarmWhiteClothing() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        BufferedImage source = new BufferedImage(40, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(new Color(229, 247, 255));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.setColor(new Color(254, 249, 242));
        graphics.fillRect(12, 20, 16, 45);
        graphics.dispose();

        BufferedImage prepared = processor.prepareTemplate(png(source));

        assertThat(prepared.getRGB(0, 0)).isZero();
        assertThat((prepared.getRGB(320, 640) >>> 24) & 0xff).isEqualTo(255);
    }

    @Test
    void deleteGeneratedCannotTraverseIntoDefaults() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(new AvatarProperties(temporaryDirectory), processor, templates);
        storage.initializeDefaults();

        storage.deleteGenerated("generated/../defaults/skin");
        assertThat(storage.stageResource(storage.defaultKey(AvatarGrowthTrack.SKIN), 1)).isNotNull();
    }

    @Test
    void stageResourceSymlinkReadsCannotEscape() throws Exception {
        AvatarImageProcessor processor = new AvatarImageProcessor();
        AvatarTemplateAssets templates = new AvatarTemplateAssets(processor);
        templates.initialize();
        AvatarStorage storage = new AvatarStorage(new AvatarProperties(temporaryDirectory), processor, templates);
        storage.initializeDefaults();

        Path outside = Files.createTempDirectory("avatar-outside-");
        Path symlink = temporaryDirectory.resolve("link");
        try {
            Files.write(outside.resolve("stage1.png"), processor.createDefaultStage(
                    templates.template(), AvatarGrowthTrack.SKIN, 1
            ));
            createSymbolicLinkOrSkip(symlink, outside);
            assertThatThrownBy(() -> storage.stageResource("link", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(symlink);
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

    private void createSymbolicLinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException exception) {
            assumeTrue(false, "Symbolic link fixtures are not supported in this test environment: "
                    + exception.getMessage());
        }
    }

    private static final class CapturingGateway implements OpenAiGateway {
        private final byte[] stageOne;
        private final byte[] laterStage;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<byte[]> inputs = new CopyOnWriteArrayList<>();
        private final List<String> prompts = new CopyOnWriteArrayList<>();
        private final List<String> sizes = new CopyOnWriteArrayList<>();
        private final List<String> qualities = new CopyOnWriteArrayList<>();
        private final List<Integer> inputCounts = new CopyOnWriteArrayList<>();
        private final List<byte[]> masks = new CopyOnWriteArrayList<>();

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
            sizes.add(size);
            qualities.add(quality);
            inputCounts.add(images.size());
            masks.add(mask.bytes());
            return calls.incrementAndGet() == 1 ? stageOne : laterStage;
        }
    }
}
