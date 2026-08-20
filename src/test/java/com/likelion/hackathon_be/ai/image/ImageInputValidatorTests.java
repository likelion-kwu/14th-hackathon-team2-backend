package com.likelion.hackathon_be.ai.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageInputValidatorTests {
    private final ImageInputValidator validator = new ImageInputValidator();

    @Test
    void acceptsDecodedPngAndNormalizesDeclaredMediaType() throws Exception {
        byte[] png = encodedImage("png");

        ValidatedImage validated = validator.validate(png, " image/png; charset=binary ");

        assertThat(validated.mediaType()).isEqualTo("image/png");
        assertThat(validated.decoded().getWidth()).isEqualTo(4);
        assertThat(validated.decoded().getHeight()).isEqualTo(3);
    }

    @Test
    void acceptsJpgAliasForDecodedJpeg() throws Exception {
        byte[] jpeg = encodedImage("jpeg");

        ValidatedImage validated = validator.validate(jpeg, "image/jpg");

        assertThat(validated.mediaType()).isEqualTo("image/jpeg");
    }

    @Test
    void rejectsPayloadOverConfiguredByteLimitBeforeContentInspection() {
        byte[] oversized = new byte[1_025];

        assertThatThrownBy(() -> validator.validate(oversized, "image/png", 1_024))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void rejectsDeclaredTypeThatDoesNotMatchMagicBytes() throws Exception {
        byte[] png = encodedImage("png");

        assertThatThrownBy(() -> validator.validate(png, "image/jpeg"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsUnsupportedImageType() {
        byte[] gif = "GIF89a-not-an-allowed-image".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> validator.validate(gif, "image/gif"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("JPEG and PNG");
    }

    @Test
    void rejectsCorruptedImageEvenWhenMagicBytesAndDeclaredTypeMatch() {
        byte[] corruptPng = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x01, 0x02, 0x03, 0x04
        };

        assertThatThrownBy(() -> validator.validate(corruptPng, "image/png"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("decoded");
    }

    @Test
    void rejectsPixelBombFromHeaderBeforeAttemptingFullDecode() throws Exception {
        byte[] headerOnlyPng = pngHeader(8_000, 6_000);

        assertThatThrownBy(() -> validator.validate(headerOnlyPng, "image/png"))
                .isInstanceOf(ImageValidationException.class)
                .hasMessageContaining("dimensions");
    }

    private byte[] encodedImage(String format) throws Exception {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] pngHeader(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        });

        ByteArrayOutputStream ihdrBytes = new ByteArrayOutputStream();
        try (DataOutputStream ihdr = new DataOutputStream(ihdrBytes)) {
            ihdr.writeInt(width);
            ihdr.writeInt(height);
            ihdr.writeByte(8);
            ihdr.writeByte(2);
            ihdr.writeByte(0);
            ihdr.writeByte(0);
            ihdr.writeByte(0);
        }
        writePngChunk(output, "IHDR", ihdrBytes.toByteArray());
        writePngChunk(output, "IEND", new byte[0]);
        return output.toByteArray();
    }

    private void writePngChunk(ByteArrayOutputStream output, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);

        DataOutputStream chunk = new DataOutputStream(output);
        chunk.writeInt(data.length);
        chunk.write(typeBytes);
        chunk.write(data);
        chunk.writeInt((int) crc.getValue());
    }
}
