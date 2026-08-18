package com.likelion.hackathon_be.ai.image;

public class ImageValidationException extends RuntimeException {
    public ImageValidationException(String message) {
        super(message);
    }

    public ImageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
