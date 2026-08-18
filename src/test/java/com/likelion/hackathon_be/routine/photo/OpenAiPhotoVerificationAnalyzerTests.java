package com.likelion.hackathon_be.routine.photo;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import javax.imageio.ImageIO;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalysis;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzerException;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiPhotoVerificationAnalyzerTests {
    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsPassedOnlyWhenAllThreeBooleansMatch() throws Exception {
        StubGateway gateway = new StubGateway();
        gateway.responses.add(objectMapper.readTree("""
                {"decidable":true,"objectMatched":true,"gestureMatched":true,"reasonCode":"MATCHED"}
                """));
        OpenAiPhotoVerificationAnalyzer analyzer = analyzer(gateway);

        PhotoVerificationAnalysis analysis = analyzer.analyze(input());

        assertThat(analysis).isEqualTo(PhotoVerificationAnalysis.success());
        assertThat(analyzer).isInstanceOf(PhotoVerificationAnalyzer.class);
        assertThat(gateway.calls).isEqualTo(1);
    }

    @Test
    void retriesOneInconsistentStructuredResult() throws Exception {
        StubGateway gateway = new StubGateway();
        gateway.responses.add(objectMapper.readTree("""
                {"decidable":true,"objectMatched":true,"gestureMatched":true,"reasonCode":"OBJECT_MISSING"}
                """));
        gateway.responses.add(objectMapper.readTree("""
                {"decidable":true,"objectMatched":false,"gestureMatched":true,"reasonCode":"OBJECT_MISSING"}
                """));

        PhotoVerificationAnalysis analysis = analyzer(gateway).analyze(input());

        assertThat(analysis).isEqualTo(new PhotoVerificationAnalysis(true, false, true));
        assertThat(gateway.calls).isEqualTo(2);
    }

    @Test
    void rejectsSpoofedMediaTypeBeforeCallingAi() throws Exception {
        StubGateway gateway = new StubGateway();
        PhotoVerificationInput valid = input();
        PhotoVerificationInput spoofed = new PhotoVerificationInput(
                valid.photoPath(), "image/jpeg", valid.verificationObject(), valid.gestureCode()
        );

        assertThatThrownBy(() -> analyzer(gateway).analyze(spoofed))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThat(gateway.calls).isZero();
    }

    @Test
    void mapsProviderFailureToPartAAnalyzerException() throws Exception {
        OpenAiGateway gateway = new FailingGateway();

        assertThatThrownBy(() -> analyzer(gateway).analyze(input()))
                .isInstanceOf(PhotoVerificationAnalyzerException.class);
    }

    private OpenAiPhotoVerificationAnalyzer analyzer(OpenAiGateway gateway) {
        return new OpenAiPhotoVerificationAnalyzer(gateway, new ImageInputValidator(), objectMapper);
    }

    private PhotoVerificationInput input() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        Path path = temporaryDirectory.resolve("verification.png");
        Files.write(path, output.toByteArray());
        return new PhotoVerificationInput(path, "image/png", "CLEANSER", "THUMBS_UP");
    }

    private static final class FailingGateway implements OpenAiGateway {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public JsonNode structuredResponse(
                String schemaName,
                String promptVersion,
                String instructions,
                String inputText,
                List<OpenAiImageInput> images,
                JsonNode schema,
                int maxOutputTokens
        ) {
            throw new com.likelion.hackathon_be.ai.openai.OpenAiGatewayException(
                    com.likelion.hackathon_be.ai.openai.OpenAiGatewayException.Kind.UNAVAILABLE,
                    "down"
            );
        }

        @Override
        public byte[] editImage(
                String promptVersion,
                String prompt,
                List<OpenAiImageInput> images,
                OpenAiImageInput mask,
                String size,
                String quality
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubGateway implements OpenAiGateway {
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private int calls;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public JsonNode structuredResponse(
                String schemaName,
                String promptVersion,
                String instructions,
                String inputText,
                List<OpenAiImageInput> images,
                JsonNode schema,
                int maxOutputTokens
        ) {
            calls++;
            return responses.remove();
        }

        @Override
        public byte[] editImage(
                String promptVersion,
                String prompt,
                List<OpenAiImageInput> images,
                OpenAiImageInput mask,
                String size,
                String quality
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
