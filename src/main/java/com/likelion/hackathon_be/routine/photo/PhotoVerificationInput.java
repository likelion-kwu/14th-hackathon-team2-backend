package com.likelion.hackathon_be.routine.photo;

import java.util.Objects;

public record PhotoVerificationInput(
        byte[] image,
        String mediaType,
        String objectCode,
        String gestureCode
) {
    public PhotoVerificationInput {
        Objects.requireNonNull(image, "image");
        image = image.clone();
        if (objectCode == null || objectCode.isBlank()) {
            throw new IllegalArgumentException("objectCode is required");
        }
        if (gestureCode == null || gestureCode.isBlank()) {
            throw new IllegalArgumentException("gestureCode is required");
        }
    }

    @Override
    public byte[] image() {
        return image.clone();
    }
}
