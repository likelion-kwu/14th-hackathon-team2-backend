package com.likelion.hackathon_be.avatar.infrastructure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        deletePath(root.resolve(".tmp"));
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
        try {
            Path realRoot = root.toRealPath();
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(realRoot)) {
                throw new IllegalArgumentException("Avatar asset symlink escapes storage root");
            }
            return new FileSystemResource(realPath);
        } catch (IOException exception) {
            return null;
        }
    }

    public void deleteGenerated(String assetSetKey) {
        if (isGeneratedKey(assetSetKey)) {
            deletePath(resolve(assetSetKey));
        }
    }

    int cleanupUnreferencedGeneratedSets(Set<String> referencedAssetSetKeys) {
        Set<String> referenced = normalizeReferencedKeys(referencedAssetSetKeys);
        Path generatedRoot = root.resolve("generated").normalize();
        ensureInsideRoot(generatedRoot);
        if (!Files.isDirectory(generatedRoot, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }

        Path realRoot;
        try {
            realRoot = root.toRealPath();
            if (!generatedRoot.toRealPath().startsWith(realRoot)) {
                return 0;
            }
        } catch (IOException exception) {
            return 0;
        }

        int deleted = 0;
        try (DirectoryStream<Path> userDirectories = Files.newDirectoryStream(generatedRoot)) {
            for (Path userDirectory : userDirectories) {
                String userId = userDirectory.getFileName().toString();
                if (!isCanonicalUserId(userId) || !isSafeDirectory(userDirectory, realRoot)) {
                    continue;
                }
                deleted += cleanupUserDirectory(userDirectory, userId, referenced, realRoot);
            }
        } catch (IOException ignored) {
            // Startup recovery is best-effort and must never broaden its deletion scope after an I/O error.
        }
        return deleted;
    }

    private Set<String> normalizeReferencedKeys(Set<String> referencedAssetSetKeys) {
        if (referencedAssetSetKeys == null || referencedAssetSetKeys.isEmpty()) {
            return Set.of();
        }
        Set<String> normalizedKeys = new HashSet<>();
        for (String assetSetKey : referencedAssetSetKeys) {
            if (assetSetKey == null || assetSetKey.isBlank()) {
                continue;
            }
            try {
                Path supplied = Path.of(assetSetKey);
                Path resolved = supplied.isAbsolute()
                        ? supplied.normalize()
                        : root.resolve(supplied).normalize();
                if (!resolved.startsWith(root)) {
                    continue;
                }
                Path relative = root.relativize(resolved);
                StringBuilder key = new StringBuilder();
                for (Path segment : relative) {
                    if (key.length() > 0) {
                        key.append('/');
                    }
                    key.append(segment);
                }
                normalizedKeys.add(key.toString());
            } catch (IllegalArgumentException ignored) {
                // Malformed DB values cannot identify a path inside this storage root.
            }
        }
        return normalizedKeys;
    }

    Path resolve(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            throw new IllegalArgumentException("Avatar asset key must be relative");
        }
        Path relative = Path.of(relativeKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Avatar asset key must be relative");
        }
        for (Path segment : relative) {
            if (".".equals(segment.toString()) || "..".equals(segment.toString())) {
                throw new IllegalArgumentException("Avatar asset key contains traversal segments");
            }
        }
        Path resolved = root.resolve(relative).normalize();
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

    private boolean isGeneratedKey(String assetSetKey) {
        if (assetSetKey == null) {
            return false;
        }
        try {
            Path key = Path.of(assetSetKey);
            if (key.isAbsolute() || key.getNameCount() != 3
                    || !"generated".equals(key.getName(0).toString())) {
                return false;
            }
            Long.parseLong(key.getName(1).toString());
            UUID.fromString(key.getName(2).toString());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private int cleanupUserDirectory(
            Path userDirectory,
            String userId,
            Set<String> referenced,
            Path realRoot
    ) {
        int deleted = 0;
        try (DirectoryStream<Path> candidates = Files.newDirectoryStream(userDirectory)) {
            for (Path candidate : candidates) {
                String key = "generated/" + userId + "/" + candidate.getFileName();
                if (!isCanonicalGeneratedKey(key)
                        || referenced.contains(key)
                        || !isCompleteGeneratedSet(candidate, realRoot)) {
                    continue;
                }
                deletePath(candidate);
                if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    deleted++;
                }
            }
        } catch (IOException ignored) {
            // Preserve this user's directories if their contents cannot be inspected safely.
        }
        return deleted;
    }

    private boolean isCompleteGeneratedSet(Path candidate, Path realRoot) {
        if (!isSafeDirectory(candidate, realRoot)) {
            return false;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(candidate)) {
            Set<String> names = new HashSet<>();
            for (Path entry : entries) {
                names.add(entry.getFileName().toString());
            }
            if (!names.equals(Set.of("stage1.png", "stage2.png", "stage3.png"))) {
                return false;
            }
            Path realCandidate = candidate.toRealPath();
            for (int stage = 1; stage <= 3; stage++) {
                Path file = candidate.resolve(stageFile(stage));
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !file.toRealPath().startsWith(realCandidate)
                        || !imageProcessor.isValidFinalPng(Files.readAllBytes(file))) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private boolean isSafeDirectory(Path directory, Path realRoot) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            return directory.toRealPath().startsWith(realRoot);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isCanonicalUserId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && Long.toString(parsed).equals(value);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isCanonicalGeneratedKey(String key) {
        try {
            Path path = Path.of(key);
            if (path.isAbsolute() || path.getNameCount() != 3
                    || !"generated".equals(path.getName(0).toString())
                    || !isCanonicalUserId(path.getName(1).toString())) {
                return false;
            }
            String uuid = path.getName(2).toString();
            return UUID.fromString(uuid).toString().equals(uuid);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
