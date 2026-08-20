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
    private byte[] identityMaskPng;
    private byte[] faceEvolutionMaskPng;

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
        this.identityMaskPng = imageProcessor.encodePng(imageProcessor.createIdentityMask());
        this.faceEvolutionMaskPng = imageProcessor.encodePng(imageProcessor.createFaceEvolutionMask());
    }

    public BufferedImage template() {
        return template;
    }

    public byte[] templatePng() {
        return templatePng.clone();
    }

    public byte[] identityMaskPng() {
        return identityMaskPng.clone();
    }

    public byte[] faceEvolutionMaskPng() {
        return faceEvolutionMaskPng.clone();
    }
}
