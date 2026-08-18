package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.image.BufferedImage;
import java.io.IOException;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class AvatarTemplateAssets {
    private final AvatarImageProcessor imageProcessor;
    private BufferedImage template;
    private byte[] templatePng;
    private byte[] faceMaskPng;

    public AvatarTemplateAssets(AvatarImageProcessor imageProcessor) {
        this.imageProcessor = imageProcessor;
    }

    @PostConstruct
    void initialize() throws IOException {
        byte[] source;
        try (var input = new ClassPathResource("avatar/canonical-human-base.png").getInputStream()) {
            source = input.readAllBytes();
        }
        this.template = imageProcessor.prepareTemplate(source);
        this.templatePng = imageProcessor.encodePng(template);
        this.faceMaskPng = imageProcessor.encodePng(imageProcessor.createFaceMask());
    }

    public BufferedImage template() {
        return template;
    }

    public byte[] templatePng() {
        return templatePng.clone();
    }

    public byte[] faceMaskPng() {
        return faceMaskPng.clone();
    }
}
