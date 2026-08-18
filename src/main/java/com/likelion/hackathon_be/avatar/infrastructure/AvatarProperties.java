package com.likelion.hackathon_be.avatar.infrastructure;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.avatar")
public record AvatarProperties(Path storageRoot) {
    public AvatarProperties {
        if (storageRoot == null) {
            storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "godsaeng-lion", "avatar");
        }
    }
}
