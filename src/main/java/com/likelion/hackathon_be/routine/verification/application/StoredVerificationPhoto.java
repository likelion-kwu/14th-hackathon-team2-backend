package com.likelion.hackathon_be.routine.verification.application;

import java.nio.file.Path;

public record StoredVerificationPhoto(
        Path path,
        String contentType
) {
}
