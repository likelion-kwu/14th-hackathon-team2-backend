package com.likelion.hackathon_be.avatar.infrastructure;

import java.io.IOException;
import java.util.List;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.image.ImageValidationException;
import com.likelion.hackathon_be.ai.image.ValidatedImage;
import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AvatarFacePhotoValidator {
    private static final String PROMPT_VERSION = "avatar-face-validation-v1";
    static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    private static final String INSTRUCTIONS = """
            Validate only whether this is a usable face reference: exactly one person, front-facing or close to
            front-facing, and enough of the face visible without major mask/hand occlusion. Do not identify the
            person and do not infer skin condition, health, age, ethnicity, emotion, attractiveness, or any
            sensitive trait. Return only the strict schema.
            """;

    private final OpenAiGateway gateway;
    private final ImageInputValidator imageValidator;
    private final AvatarFaceReferenceNormalizer faceReferenceNormalizer;
    private final JsonNode schema;

    public AvatarFacePhotoValidator(
            OpenAiGateway gateway,
            ImageInputValidator imageValidator,
            AvatarFaceReferenceNormalizer faceReferenceNormalizer,
            ObjectMapper objectMapper
    ) {
        this.gateway = gateway;
        this.imageValidator = imageValidator;
        this.faceReferenceNormalizer = faceReferenceNormalizer;
        this.schema = objectMapper.readTree("""
                {
                  "type":"object",
                  "additionalProperties":false,
                  "properties":{
                    "singlePerson":{"type":"boolean"},
                    "nearFrontal":{"type":"boolean"},
                    "faceVisible":{"type":"boolean"}
                  },
                  "required":["singlePerson","nearFrontal","faceVisible"]
                }
                """);
    }

    public AvatarFaceReference validate(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }
        if (photo.getSize() > MAX_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.AVATAR_FACE_PHOTO_INVALID);
        }
        ValidatedImage image;
        try {
            image = imageValidator.validate(photo.getBytes(), photo.getContentType(), MAX_UPLOAD_BYTES);
        } catch (IOException | ImageValidationException exception) {
            throw new BusinessException(ErrorCode.AVATAR_FACE_PHOTO_INVALID);
        }
        AvatarFaceReference faceReference = faceReferenceNormalizer.normalize(image);

        for (int attempt = 0; attempt < 2; attempt++) {
            JsonNode result;
            try {
                result = gateway.structuredResponse(
                        "avatar_face_validation",
                        PROMPT_VERSION,
                        INSTRUCTIONS,
                        attempt == 0 ? "Check this face reference." : "The previous output was invalid. Re-evaluate.",
                        List.of(new OpenAiImageInput(
                                faceReference.bytes(),
                                faceReference.mediaType(),
                                "high"
                        )),
                        schema,
                        100
                );
            } catch (OpenAiGatewayException exception) {
                if (exception.kind() == OpenAiGatewayException.Kind.INVALID_RESPONSE && attempt == 0) {
                    continue;
                }
                throw exception;
            }
            if (hasBoolean(result, "singlePerson")
                    && hasBoolean(result, "nearFrontal")
                    && hasBoolean(result, "faceVisible")) {
                if (!result.path("singlePerson").booleanValue()
                        || !result.path("nearFrontal").booleanValue()
                        || !result.path("faceVisible").booleanValue()) {
                    throw new BusinessException(ErrorCode.AVATAR_FACE_PHOTO_INVALID);
                }
                return faceReference;
            }
        }
        throw new IllegalStateException("Invalid face validation response");
    }

    private boolean hasBoolean(JsonNode node, String field) {
        return node.has(field) && node.path(field).isBoolean();
    }
}
