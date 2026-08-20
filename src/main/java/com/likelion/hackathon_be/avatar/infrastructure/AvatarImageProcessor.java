package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import javax.imageio.ImageIO;

import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import org.springframework.stereotype.Component;

@Component
public class AvatarImageProcessor {
    public static final int WORK_WIDTH = 640;
    public static final int WORK_HEIGHT = 1280;
    public static final int FINAL_WIDTH = 250;
    public static final int FINAL_HEIGHT = 500;
    private static final int MIN_FOREGROUND_ALPHA = 128;
    private static final int MAX_BACKGROUND_ALPHA = 16;
    private static final int MIN_FOREGROUND_PIXELS = FINAL_WIDTH * FINAL_HEIGHT / 20;
    private static final int MIN_TRANSPARENT_PIXELS = FINAL_WIDTH * FINAL_HEIGHT / 5;
    private static final int MIN_FOREGROUND_WIDTH = FINAL_WIDTH / 4;
    private static final int MIN_FOREGROUND_HEIGHT = FINAL_HEIGHT / 2;

    public BufferedImage prepareTemplate(byte[] source) {
        BufferedImage decoded = decode(source);
        BufferedImage scaled = resize(decoded, WORK_WIDTH, WORK_HEIGHT);
        return removeConnectedLightBackground(scaled);
    }

    public BufferedImage createIdentityMask() {
        BufferedImage mask = new BufferedImage(WORK_WIDTH, WORK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = mask.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 255));
        graphics.fillRect(0, 0, WORK_WIDTH, WORK_HEIGHT);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setComposite(java.awt.AlphaComposite.Clear);
        Area identityArea = new Area(new Ellipse2D.Double(135, 40, 370, 470));
        identityArea.add(new Area(new Ellipse2D.Double(115, 155, 220, 500)));
        identityArea.add(new Area(new Ellipse2D.Double(305, 155, 220, 500)));
        graphics.fill(identityArea);
        graphics.dispose();
        return mask;
    }

    public BufferedImage createFaceEvolutionMask() {
        BufferedImage mask = new BufferedImage(WORK_WIDTH, WORK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = mask.createGraphics();
        graphics.setColor(new Color(0, 0, 0, 255));
        graphics.fillRect(0, 0, WORK_WIDTH, WORK_HEIGHT);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setComposite(java.awt.AlphaComposite.Clear);
        graphics.fill(new Ellipse2D.Double(195, 125, 250, 365));
        graphics.dispose();
        return mask;
    }

    public byte[] normalizeGenerated(byte[] generated, BufferedImage template) {
        BufferedImage image = resize(decode(generated), WORK_WIDTH, WORK_HEIGHT);
        BufferedImage rgba = new BufferedImage(WORK_WIDTH, WORK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < WORK_HEIGHT; y++) {
            for (int x = 0; x < WORK_WIDTH; x++) {
                int alpha = (template.getRGB(x, y) >>> 24) & 0xff;
                rgba.setRGB(x, y, (alpha << 24) | (image.getRGB(x, y) & 0x00ffffff));
            }
        }
        return encodePng(resize(rgba, FINAL_WIDTH, FINAL_HEIGHT));
    }

    public byte[] composeMaskedEdit(byte[] generated, BufferedImage base, BufferedImage mask) {
        return composeMaskedEdit(generated, base, mask, 1.0d);
    }

    public byte[] composeMaskedEdit(
            byte[] generated,
            BufferedImage base,
            BufferedImage mask,
            double editStrength
    ) {
        if (editStrength < 0.0d || editStrength > 1.0d) {
            throw new IllegalArgumentException("Edit strength must be between zero and one");
        }
        BufferedImage edited = removeConnectedLightBackground(
                resize(decode(generated), WORK_WIDTH, WORK_HEIGHT)
        );
        BufferedImage normalizedBase = resize(base, WORK_WIDTH, WORK_HEIGHT);
        BufferedImage normalizedMask = resize(mask, WORK_WIDTH, WORK_HEIGHT);
        BufferedImage composed = new BufferedImage(WORK_WIDTH, WORK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < WORK_HEIGHT; y++) {
            for (int x = 0; x < WORK_WIDTH; x++) {
                int baseArgb = normalizedBase.getRGB(x, y);
                int editedArgb = edited.getRGB(x, y);
                int maskEditWeight = 255 - ((normalizedMask.getRGB(x, y) >>> 24) & 0xff);
                int editWeight = (int) Math.round(maskEditWeight * editStrength);
                int keepWeight = 255 - editWeight;
                int red = blend((baseArgb >>> 16) & 0xff, (editedArgb >>> 16) & 0xff, keepWeight, editWeight);
                int green = blend((baseArgb >>> 8) & 0xff, (editedArgb >>> 8) & 0xff, keepWeight, editWeight);
                int blue = blend(baseArgb & 0xff, editedArgb & 0xff, keepWeight, editWeight);
                int alpha = blend(
                        (baseArgb >>> 24) & 0xff,
                        (editedArgb >>> 24) & 0xff,
                        keepWeight,
                        editWeight
                );
                composed.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return encodePng(composed);
    }

    public byte[] toFinalPng(byte[] workImage) {
        return encodePng(resize(decode(workImage), FINAL_WIDTH, FINAL_HEIGHT));
    }

    public byte[] createDefaultStage(BufferedImage template, AvatarGrowthTrack track, int stage) {
        BufferedImage copy = new BufferedImage(WORK_WIDTH, WORK_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(template, 0, 0, null);
        if (track == AvatarGrowthTrack.HEALTH_FIT || track == AvatarGrowthTrack.DIET) {
            applyFixedFaceOutlinePreset(graphics, template, stage);
        }
        graphics.setComposite(java.awt.AlphaComposite.SrcAtop.derive(defaultTintAlpha(stage)));
        graphics.setColor(defaultTint(track, stage));
        graphics.fillOval(240, 105, 160, 205);
        graphics.dispose();
        return encodePng(resize(copy, FINAL_WIDTH, FINAL_HEIGHT));
    }

    private void applyFixedFaceOutlinePreset(Graphics2D graphics, BufferedImage template, int stage) {
        double horizontalScale = switch (stage) {
            case 1 -> 1.055d;
            case 2 -> 1.0d;
            default -> 0.955d;
        };
        if (horizontalScale == 1.0d) {
            return;
        }
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setClip(new Ellipse2D.Double(220, 75, 200, 270));
        AffineTransform transform = new AffineTransform();
        transform.translate(WORK_WIDTH / 2.0d, 0);
        transform.scale(horizontalScale, 1.0d);
        transform.translate(-WORK_WIDTH / 2.0d, 0);
        graphics.drawImage(template, transform, null);
        graphics.setClip(null);
    }

    public byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG writer is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode avatar PNG", exception);
        }
    }

    public BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Invalid image data");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid image data", exception);
        }
    }

    public boolean isValidFinalPng(byte[] bytes) {
        if (!hasPngSignature(bytes)) {
            return false;
        }
        BufferedImage image;
        try {
            image = decode(bytes);
        } catch (RuntimeException exception) {
            return false;
        }

        if (image.getWidth() != FINAL_WIDTH
                || image.getHeight() != FINAL_HEIGHT
                || !image.getColorModel().hasAlpha()) {
            return false;
        }

        int foregroundPixels = 0;
        int transparentPixels = 0;
        int minX = FINAL_WIDTH;
        int minY = FINAL_HEIGHT;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha <= MAX_BACKGROUND_ALPHA) {
                    transparentPixels++;
                }
                if (alpha >= MIN_FOREGROUND_ALPHA) {
                    foregroundPixels++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (foregroundPixels < MIN_FOREGROUND_PIXELS
                || transparentPixels < MIN_TRANSPARENT_PIXELS
                || maxX < minX
                || maxY < minY) {
            return false;
        }
        return maxX - minX + 1 >= MIN_FOREGROUND_WIDTH
                && maxY - minY + 1 >= MIN_FOREGROUND_HEIGHT;
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private BufferedImage removeConnectedLightBackground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean skyBlueBackground = hasSkyBlueBorder(source);
        boolean[][] background = new boolean[height][width];
        Queue<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(source, background, queue, x, 0, skyBlueBackground);
            enqueueBackground(source, background, queue, x, height - 1, skyBlueBackground);
        }
        for (int y = 0; y < height; y++) {
            enqueueBackground(source, background, queue, 0, y, skyBlueBackground);
            enqueueBackground(source, background, queue, width - 1, y, skyBlueBackground);
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            for (int[] direction : directions) {
                int x = point[0] + direction[0];
                int y = point[1] + direction[1];
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    enqueueBackground(source, background, queue, x, y, skyBlueBackground);
                }
            }
        }

        BufferedImage rgba = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int rgb = argb & 0x00ffffff;
                int alpha = (argb >>> 24) & 0xff;
                // Transparent matte pixels must not retain the blue/white RGB value. Keeping hidden matte
                // color causes a fringe when a non-premultiplied ARGB image is resized or composited later.
                rgba.setRGB(x, y, background[y][x] ? 0 : (alpha << 24) | rgb);
            }
        }
        return rgba;
    }

    private void enqueueBackground(
            BufferedImage source,
            boolean[][] background,
            Queue<int[]> queue,
            int x,
            int y,
            boolean skyBlueBackground
    ) {
        if (background[y][x] || !isBackgroundPixel(source.getRGB(x, y), skyBlueBackground)) {
            return;
        }
        background[y][x] = true;
        queue.add(new int[]{x, y});
    }

    private boolean hasSkyBlueBorder(BufferedImage source) {
        int skyBlueSamples = 0;
        int samples = 0;
        int horizontalStep = Math.max(1, source.getWidth() / 32);
        int verticalStep = Math.max(1, source.getHeight() / 32);
        for (int x = 0; x < source.getWidth(); x += horizontalStep) {
            skyBlueSamples += isSkyBlue(source.getRGB(x, 0)) ? 1 : 0;
            skyBlueSamples += isSkyBlue(source.getRGB(x, source.getHeight() - 1)) ? 1 : 0;
            samples += 2;
        }
        for (int y = 0; y < source.getHeight(); y += verticalStep) {
            skyBlueSamples += isSkyBlue(source.getRGB(0, y)) ? 1 : 0;
            skyBlueSamples += isSkyBlue(source.getRGB(source.getWidth() - 1, y)) ? 1 : 0;
            samples += 2;
        }
        return skyBlueSamples * 2 >= samples;
    }

    private boolean isBackgroundPixel(int argb, boolean skyBlueBackground) {
        return skyBlueBackground ? isSkyBlue(argb) : isLightNeutral(argb);
    }

    private boolean isSkyBlue(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha == 0) {
            return true;
        }
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        return red >= 150
                && green >= 175
                && blue >= 205
                && blue - red >= 18
                && blue - green >= 4;
    }

    private boolean isLightNeutral(int argb) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha == 0) {
            return true;
        }
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        return min >= 190 && max - min <= 45;
    }

    private Color defaultTint(AvatarGrowthTrack track, int stage) {
        return switch (track) {
            case SKIN -> new Color(255, 224 + stage * 7, 210 + stage * 10);
            case WELL_BEING -> new Color(230 + stage * 7, 225 + stage * 8, 205);
            case HEALTH_FIT -> new Color(220, 235 + stage * 5, 255);
            case DIET -> new Color(225, 245, 220 + stage * 8);
        };
    }

    private float defaultTintAlpha(int stage) {
        return switch (stage) {
            case 1 -> 0.18f;
            case 2 -> 0.12f;
            default -> 0.07f;
        };
    }

    private int blend(int base, int edited, int keepWeight, int editWeight) {
        return (base * keepWeight + edited * editWeight + 127) / 255;
    }

    private boolean hasPngSignature(byte[] bytes) {
        int[] signature = {0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes == null || bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xff) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
