package com.likelion.hackathon_be.ai.openai;

public class OpenAiGatewayException extends RuntimeException {
    public enum Kind {
        UNAVAILABLE,
        REFUSED,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public OpenAiGatewayException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public OpenAiGatewayException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
