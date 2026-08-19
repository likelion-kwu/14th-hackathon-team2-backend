package com.likelion.hackathon_be.avatar.infrastructure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import javax.imageio.ImageIO;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarFacePhotoValidatorTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void optionalPhotoSkipsVisionValidation() {
        StubGateway gateway = new StubGateway();

        AvatarFaceReference reference = validator(gateway).validate(null);

        assertThat(reference).isNull();
        assertThat(gateway.calls).isZero();
    }

    @Test
    void rejectsInvalidImageBeforeVisionValidation() {
        StubGateway gateway = new StubGateway();
        MockMultipartFile invalid = new MockMultipartFile(
                "facePhoto", "face.png", "image/png", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> validator(gateway).validate(invalid))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_FACE_PHOTO_INVALID));
        assertThat(gateway.calls).isZero();
    }

    @Test
    void rejectsFaceThatDoesNotMeetReferenceGuidance() throws Exception {
        StubGateway gateway = new StubGateway();
        gateway.responses.add(objectMapper.readTree("""
                {"singlePerson":true,"nearFrontal":false,"faceVisible":true}
                """));

        assertThatThrownBy(() -> validator(gateway).validate(photo()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_FACE_PHOTO_INVALID));
        assertThat(gateway.calls).isEqualTo(1);
    }

    @Test
    void retriesOneInvalidSchemaAndReturnsDefensiveHighDetailReference() throws Exception {
        StubGateway gateway = new StubGateway();
        gateway.responses.add(objectMapper.readTree("""
                {"singlePerson":true,"nearFrontal":true}
                """));
        gateway.responses.add(objectMapper.readTree("""
                {"singlePerson":true,"nearFrontal":true,"faceVisible":true}
                """));
        MockMultipartFile photo = photo();

        AvatarFaceReference reference = validator(gateway).validate(photo);

        assertThat(reference.mediaType()).isEqualTo("image/png");
        assertThat(reference.bytes()).containsExactly(photo.getBytes());
        assertThat(gateway.calls).isEqualTo(2);
        assertThat(gateway.images).allSatisfy(images -> assertThat(images).singleElement()
                .satisfies(image -> assertThat(image.detail()).isEqualTo("high")));
        byte[] exposed = reference.bytes();
        exposed[0] = 0;
        assertThat(reference.bytes()[0]).isNotZero();
    }

    @Test
    void providerFailureRemainsAvailableForInitialFallbackOrRegenerationMapping() throws Exception {
        OpenAiGateway gateway = new FailingGateway();

        assertThatThrownBy(() -> validator(gateway).validate(photo()))
                .isInstanceOfSatisfying(OpenAiGatewayException.class, exception ->
                        assertThat(exception.kind()).isEqualTo(OpenAiGatewayException.Kind.UNAVAILABLE));
    }

    private AvatarFacePhotoValidator validator(OpenAiGateway gateway) {
        return new AvatarFacePhotoValidator(gateway, new ImageInputValidator(), objectMapper);
    }

    private MockMultipartFile photo() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("facePhoto", "face.png", "image/png", output.toByteArray());
    }

    private static final class StubGateway implements OpenAiGateway {
        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private final List<List<OpenAiImageInput>> images = new ArrayList<>();
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
            this.images.add(List.copyOf(images));
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
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "provider unavailable");
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
