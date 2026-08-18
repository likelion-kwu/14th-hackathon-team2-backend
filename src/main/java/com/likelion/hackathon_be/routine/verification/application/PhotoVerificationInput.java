package com.likelion.hackathon_be.routine.verification.application;

import java.nio.file.Path;

public record PhotoVerificationInput(
        Path photoPath,
        String contentType,
        String verificationObject,
        String gestureCode
) {
}
