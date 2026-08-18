package com.likelion.hackathon_be.routine.verification.application;

import java.util.Arrays;
import java.util.Objects;

public record StoredVerificationPhoto(
        byte[] image,
        String mediaType
) {
    public StoredVerificationPhoto {
        Objects.requireNonNull(image, "image");
        if (image.length > PhotoVerificationInput.MAX_IMAGE_BYTES) {
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
