package com.likelion.hackathon_be.speech.infrastructure;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
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
        setPermissions(root, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        ), null);
    }

    public void save(UUID jobId, KakaoChatData data) {
        Path directory = jobDirectory(jobId);
        Path temporary = directory.resolve("chat.json.part");
        Path target = directory.resolve("chat.json");
        try {
            Files.createDirectories(directory);
            setPermissions(directory, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            ), jobId);
            objectMapper.writeValue(temporary.toFile(), data);
            setPermissions(temporary, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ), jobId);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
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
        AtomicBoolean failed = new AtomicBoolean();
        try {
            if (!Files.exists(directory)) {
                return;
            }
            try (var stream = Files.walk(directory)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        failed.set(true);
                    }
                });
            }
        } catch (IOException exception) {
            failed.set(true);
        }
        if (failed.get()) {
            log.warn("speech_temp_cleanup_failed jobId={} errorCode=DELETE_FAILED", jobId);
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
        } catch (IOException exception) {
            log.warn("speech_temp_cleanup_scan_failed errorCode={}", exception.getClass().getSimpleName());
        }
    }

    private Path jobDirectory(UUID jobId) {
        Path path = root.resolve(jobId.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid speech job path");
        }
        return path;
    }

    private void setPermissions(Path path, Set<PosixFilePermission> permissions, UUID jobId) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on the service account and work-root ACL.
        } catch (IOException exception) {
            log.warn(
                    "speech_temp_permission_failed jobId={} errorCode={}",
                    jobId,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
