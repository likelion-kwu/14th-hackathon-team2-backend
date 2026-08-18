package com.likelion.hackathon_be.routine.verification.application;

import java.util.Arrays;
import java.util.Objects;

public record PhotoVerificationInput(
        byte[] image,
        String mediaType,
        String objectCode,
        String gestureCode
) {
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    public PhotoVerificationInput {
        Objects.requireNonNull(image, "image");
        if (image.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Verification photo exceeds the size limit");
        }
        image = image.clone();
    }

    @Override
    public byte[] image() {
        return image.clone();
    }

    public void destroy() {
        Arrays.fill(image, (byte) 0);
    }
}
