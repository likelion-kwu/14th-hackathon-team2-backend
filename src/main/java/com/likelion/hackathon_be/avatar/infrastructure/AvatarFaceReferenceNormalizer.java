package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.image.ValidatedImage;
import org.springframework.stereotype.Component;

@Component
public class AvatarFaceReferenceNormalizer {
    static final int MAX_EDGE = 1_024;
    private static final float JPEG_QUALITY = 0.9f;

    public AvatarFaceReference normalize(ValidatedImage image) {
        BufferedImage decoded = image.decoded();
        if (image.bytes().length <= ImageInputValidator.DEFAULT_MAX_BYTES
                && Math.max(decoded.getWidth(), decoded.getHeight()) <= MAX_EDGE) {
            return new AvatarFaceReference(image.bytes(), image.mediaType());
        }

        double scale = Math.min(1.0d, (double) MAX_EDGE / Math.max(decoded.getWidth(), decoded.getHeight()));
        int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
        BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = normalized.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(decoded, 0, 0, width, height, null);
        graphics.dispose();

        byte[] encoded = encodeJpeg(normalized);
        if (encoded.length > ImageInputValidator.DEFAULT_MAX_BYTES) {
            throw new IllegalStateException("Normalized avatar face reference exceeds the size limit");
        }
        return new AvatarFaceReference(encoded, "image/jpeg");
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG writer is unavailable");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot normalize avatar face reference", exception);
        } finally {
            writer.dispose();
        }
    }
}
