package com.likelion.hackathon_be.speech.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class KakaoTemporaryStore {
    private final Path root;
    private final ObjectMapper objectMapper;

    public KakaoTemporaryStore(SpeechProperties properties, ObjectMapper objectMapper) {
        this.root = properties.workRoot().toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    public void save(UUID jobId, KakaoChatData data) {
        Path directory = jobDirectory(jobId);
        Path temporary = directory.resolve("chat.json.part");
        Path target = directory.resolve("chat.json");
        try {
            Files.createDirectories(directory);
            objectMapper.writeValue(temporary.toFile(), data);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException exception) {
            delete(jobId);
            throw new IllegalStateException("Cannot store temporary chat data", exception);
        }
    }

    public KakaoChatData read(UUID jobId) {
        try {
            return objectMapper.readValue(jobDirectory(jobId).resolve("chat.json").toFile(), KakaoChatData.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Temporary chat data is unavailable", exception);
        }
    }

    public void delete(UUID jobId) {
        Path directory = jobDirectory(jobId);
        try {
            if (!Files.exists(directory)) {
                return;
            }
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best-effort privacy cleanup, retried by expiration recovery.
                    }
                });
            }
        } catch (IOException ignored) {
            // Best-effort privacy cleanup, retried by expiration recovery.
        }
    }

    public void cleanupExcept(Set<UUID> activeJobIds) {
        try (var directories = Files.list(root)) {
            directories.filter(Files::isDirectory).forEach(path -> {
                try {
                    UUID jobId = UUID.fromString(path.getFileName().toString());
                    if (!activeJobIds.contains(jobId)) {
                        delete(jobId);
                    }
                } catch (IllegalArgumentException ignored) {
                    // The dedicated work root only deletes directories created with a known job UUID.
                }
            });
        } catch (IOException ignored) {
            // The next scheduled recovery retries cleanup.
        }
    }

    private Path jobDirectory(UUID jobId) {
        Path path = root.resolve(jobId.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid speech job path");
        }
        return path;
    }
}
