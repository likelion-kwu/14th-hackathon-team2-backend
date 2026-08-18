package com.likelion.hackathon_be.avatar.infrastructure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class AvatarStorage {
    private final Path root;
    private final AvatarImageProcessor imageProcessor;
    private final AvatarTemplateAssets templateAssets;

    public AvatarStorage(
            AvatarProperties properties,
            AvatarImageProcessor imageProcessor,
            AvatarTemplateAssets templateAssets
    ) {
        this.root = properties.storageRoot().toAbsolutePath().normalize();
        this.imageProcessor = imageProcessor;
        this.templateAssets = templateAssets;
    }

    @PostConstruct
    void initializeDefaults() throws IOException {
        Files.createDirectories(root);
        Files.createDirectories(root.resolve(".tmp"));
        for (AvatarGrowthTrack track : AvatarGrowthTrack.values()) {
            Path directory = resolve(defaultKey(track));
            Files.createDirectories(directory);
            for (int stage = 1; stage <= 3; stage++) {
                Path target = directory.resolve(stageFile(stage));
                byte[] asset = imageProcessor.createDefaultStage(templateAssets.template(), track, stage);
                writeValidated(target, asset);
            }
        }
    }

    public String defaultKey(AvatarGrowthTrack track) {
        return "defaults/" + track.name().toLowerCase();
    }

    public String storeGenerated(Long userId, List<byte[]> stages) {
        if (stages.size() != 3) {
            throw new IllegalArgumentException("Exactly three avatar stages are required");
        }
        String id = UUID.randomUUID().toString();
        Path temporary = resolve(".tmp/" + id);
        String finalKey = "generated/" + userId + "/" + id;
        Path destination = resolve(finalKey);
        try {
            Files.createDirectories(temporary);
            for (int index = 0; index < stages.size(); index++) {
                writeValidated(temporary.resolve(stageFile(index + 1)), stages.get(index));
            }
            Files.createDirectories(destination.getParent());
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            return finalKey;
        } catch (IOException | RuntimeException exception) {
            deletePath(temporary);
            deletePath(destination);
            throw new IllegalStateException("Cannot persist generated avatar set", exception);
        }
    }

    public Resource stageResource(String assetSetKey, int stage) {
        Path path = resolve(assetSetKey).resolve(stageFile(stage)).normalize();
        ensureInsideRoot(path);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return new FileSystemResource(path);
    }

    public void deleteGenerated(String assetSetKey) {
        if (assetSetKey != null && assetSetKey.startsWith("generated/")) {
            deletePath(resolve(assetSetKey));
        }
    }

    Path resolve(String relativeKey) {
        if (relativeKey == null || Path.of(relativeKey).isAbsolute()) {
            throw new IllegalArgumentException("Avatar asset key must be relative");
        }
        Path resolved = root.resolve(relativeKey).normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void writeValidated(Path target, byte[] bytes) throws IOException {
        if (!imageProcessor.isValidFinalPng(bytes)) {
            throw new IllegalArgumentException("Avatar asset must be a 250x500 RGBA PNG");
        }
        Files.write(target, bytes);
    }

    private String stageFile(int stage) {
        if (stage < 1 || stage > 3) {
            throw new IllegalArgumentException("Invalid avatar stage");
        }
        return "stage" + stage + ".png";
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Avatar asset path escapes storage root");
        }
    }

    private void deletePath(Path path) {
        try {
            if (!Files.exists(path)) {
                return;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                    try {
                        Files.deleteIfExists(candidate);
                    } catch (IOException ignored) {
                        // Best-effort cleanup; no uploaded source image is stored here.
                    }
                });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
