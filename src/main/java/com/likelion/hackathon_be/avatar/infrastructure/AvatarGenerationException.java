package com.likelion.hackathon_be.avatar.infrastructure;

public class AvatarGenerationException extends RuntimeException {
    public AvatarGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AvatarGenerationException(String message) {
        super(message);
    }
}
