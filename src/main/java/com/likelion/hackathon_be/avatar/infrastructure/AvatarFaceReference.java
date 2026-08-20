package com.likelion.hackathon_be.avatar.infrastructure;

import java.util.Objects;

public record AvatarFaceReference(byte[] bytes, String mediaType) {
    public AvatarFaceReference {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mediaType, "mediaType");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
