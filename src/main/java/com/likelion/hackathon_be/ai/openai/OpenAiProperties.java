package com.likelion.hackathon_be.ai.openai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String responsesModel,
        String imageModel,
        Duration connectTimeout,
        Duration responseTimeout,
        Duration imageTimeout
) {
    public OpenAiProperties {
        baseUrl = defaultIfBlank(baseUrl, "https://api.openai.com");
        responsesModel = defaultIfBlank(responsesModel, "gpt-5.6-luna");
        imageModel = defaultIfBlank(imageModel, "gpt-image-2");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(60) : responseTimeout;
        imageTimeout = imageTimeout == null ? Duration.ofSeconds(130) : imageTimeout;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
