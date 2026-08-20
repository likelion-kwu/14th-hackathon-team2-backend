package com.likelion.hackathon_be.ai.image;

import java.awt.image.BufferedImage;

public record ValidatedImage(byte[] bytes, String mediaType, BufferedImage decoded) {
    public ValidatedImage {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
