package com.likelion.hackathon_be.ai.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

@Component
public class ImageInputValidator {
    public static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_EDGE_PIXELS = 10_000;
    private static final long MAX_TOTAL_PIXELS = 40_000_000L;

    public ValidatedImage validate(byte[] bytes, String declaredMediaType) {
        return validate(bytes, declaredMediaType, DEFAULT_MAX_BYTES);
    }

    public ValidatedImage validate(byte[] bytes, String declaredMediaType, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            throw new ImageValidationException("Image is empty");
        }
        if (bytes.length > maxBytes) {
            throw new ImageValidationException("Image exceeds the size limit");
        }

        String actualMediaType = detectMediaType(bytes);
        String normalizedDeclared = normalizeMediaType(declaredMediaType);
        if (normalizedDeclared == null || !normalizedDeclared.equals(actualMediaType)) {
            throw new ImageValidationException("Image media type does not match its content");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new ImageValidationException("Image cannot be decoded");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ImageValidationException("Image cannot be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_EDGE_PIXELS || height > MAX_EDGE_PIXELS
                        || (long) width * height > MAX_TOTAL_PIXELS) {
                    throw new ImageValidationException("Image dimensions exceed the safety limit");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new ImageValidationException("Image cannot be decoded");
                }
                return new ValidatedImage(bytes, actualMediaType, decoded);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ImageValidationException("Image cannot be decoded", exception);
        }
    }

    private String detectMediaType(byte[] bytes) {
        if (isJpeg(bytes)) {
            return "image/jpeg";
        }
        if (isPng(bytes)) {
            return "image/png";
        }
        throw new ImageValidationException("Only JPEG and PNG images are supported");
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 4
                && unsigned(bytes[0]) == 0xff
                && unsigned(bytes[1]) == 0xd8
                && unsigned(bytes[bytes.length - 2]) == 0xff
                && unsigned(bytes[bytes.length - 1]) == 0xd9;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(bytes[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null) {
            return null;
        }
        String normalized = mediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}
