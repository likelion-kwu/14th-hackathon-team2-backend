package com.likelion.hackathon_be.ai.openai;

import java.util.Base64;
import java.util.Objects;

public record OpenAiImageInput(byte[] bytes, String mediaType) {
    public OpenAiImageInput {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
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
