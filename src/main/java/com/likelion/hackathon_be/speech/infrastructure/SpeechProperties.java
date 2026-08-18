package com.likelion.hackathon_be.speech.infrastructure;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.speech")
public record SpeechProperties(Path workRoot) {
    public SpeechProperties {
        if (workRoot == null) {
            workRoot = Path.of(System.getProperty("java.io.tmpdir"), "godsaeng-lion", "speech");
        }
    }
}
