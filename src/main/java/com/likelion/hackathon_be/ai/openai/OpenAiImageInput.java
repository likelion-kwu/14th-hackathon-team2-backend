package com.likelion.hackathon_be.ai.openai;

import java.util.Base64;
import java.util.Objects;

public record OpenAiImageInput(byte[] bytes, String mediaType, String detail) {
    public OpenAiImageInput(byte[] bytes, String mediaType) {
        this(bytes, mediaType, "low");
    }

    public OpenAiImageInput {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        if (!java.util.Set.of("low", "high", "auto", "original").contains(detail)) {
            throw new IllegalArgumentException("Unsupported image detail level");
        }
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public String dataUrl() {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
