package com.likelion.hackathon_be.routine.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import com.likelion.hackathon_be.ai.image.ImageInputValidator;
import com.likelion.hackathon_be.ai.image.ImageValidationException;
import com.likelion.hackathon_be.ai.image.ValidatedImage;
import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiGatewayException;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalysis;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzer;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationAnalyzerException;
import com.likelion.hackathon_be.routine.verification.application.PhotoVerificationInput;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OpenAiPhotoVerificationAnalyzer implements PhotoVerificationAnalyzer {
    private static final String PROMPT_VERSION = "photo-verification-v1";
    private static final String INSTRUCTIONS = """
            You verify a routine proof photo using only visible evidence. Determine only whether the specified
            object is visible, whether the specified hand gesture is visible, and whether the image is clear
            enough to decide. Never identify a person or infer skin, health, ethnicity, age, emotion, or other
            sensitive traits. If the image is blurred, occluded, or ambiguous, mark decidable false. Return only
            the provided strict JSON schema.
            """;

    private final OpenAiGateway gateway;
    private final ImageInputValidator imageValidator;
    private final JsonNode schema;

    public OpenAiPhotoVerificationAnalyzer(
            OpenAiGateway gateway,
            ImageInputValidator imageValidator,
            ObjectMapper objectMapper
    ) {
        this.gateway = gateway;
        this.imageValidator = imageValidator;
        try {
            this.schema = objectMapper.readTree("""
                    {
                      "type":"object",
                      "additionalProperties":false,
                      "properties":{
                        "decidable":{"type":"boolean"},
                        "objectMatched":{"type":"boolean"},
                        "gestureMatched":{"type":"boolean"},
                        "reasonCode":{"type":"string","enum":[
                          "MATCHED","OBJECT_MISSING","GESTURE_MISSING","BOTH_MISSING",
                          "IMAGE_UNCLEAR","MODEL_REFUSED"
                        ]}
                      },
                      "required":["decidable","objectMatched","gestureMatched","reasonCode"]
                    }
                    """);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create photo verification schema", exception);
        }
    }

    @Override
    public PhotoVerificationAnalysis analyze(PhotoVerificationInput input) {
        byte[] photo = readPhoto(input);
        ValidatedImage image;
        try {
            image = imageValidator.validate(photo, input.contentType());
        } catch (ImageValidationException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String request = "Required object code: " + input.verificationObject()
                + "\nRequired gesture code: " + input.gestureCode();
        for (int schemaAttempt = 0; schemaAttempt < 2; schemaAttempt++) {
            try {
                JsonNode result = gateway.structuredResponse(
                        "photo_verification",
                        PROMPT_VERSION,
                        INSTRUCTIONS,
                        schemaAttempt == 0 ? request : request + "\nYour previous result was inconsistent. Re-evaluate.",
                        List.of(new OpenAiImageInput(image.bytes(), image.mediaType())),
                        schema,
                        160
                );
                PhotoVerificationDecision decision = parse(result);
                if (consistent(decision)) {
                    return new PhotoVerificationAnalysis(
                            decision.decidable(),
                            decision.objectMatched(),
                            decision.gestureMatched()
                    );
                }
            } catch (OpenAiGatewayException exception) {
                if (exception.kind() == OpenAiGatewayException.Kind.REFUSED) {
                    return new PhotoVerificationAnalysis(false, false, false);
                }
                if (exception.kind() == OpenAiGatewayException.Kind.INVALID_RESPONSE && schemaAttempt == 0) {
                    continue;
                }
                throw new PhotoVerificationAnalyzerException("OpenAI photo verification is unavailable", exception);
            } catch (RuntimeException exception) {
                if (schemaAttempt == 1) {
                    throw new PhotoVerificationAnalyzerException("OpenAI photo verification response is invalid", exception);
                }
            }
        }
        throw new PhotoVerificationAnalyzerException("OpenAI photo verification response is invalid");
    }

    private byte[] readPhoto(PhotoVerificationInput input) {
        try {
            if (Files.size(input.photoPath()) > ImageInputValidator.DEFAULT_MAX_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            return Files.readAllBytes(input.photoPath());
        } catch (IOException exception) {
            throw new PhotoVerificationAnalyzerException("Cannot read temporary verification photo", exception);
        }
    }

    private PhotoVerificationDecision parse(JsonNode result) {
        return new PhotoVerificationDecision(
                requiredBoolean(result, "decidable"),
                requiredBoolean(result, "objectMatched"),
                requiredBoolean(result, "gestureMatched"),
                PhotoVerificationDecision.ReasonCode.valueOf(requiredText(result, "reasonCode"))
        );
    }

    private boolean consistent(PhotoVerificationDecision decision) {
        if (!decision.decidable()) {
            return !decision.objectMatched()
                    && !decision.gestureMatched()
                    && (decision.reasonCode() == PhotoVerificationDecision.ReasonCode.IMAGE_UNCLEAR
                    || decision.reasonCode() == PhotoVerificationDecision.ReasonCode.MODEL_REFUSED);
        }
        if (decision.objectMatched() && decision.gestureMatched()) {
            return decision.reasonCode() == PhotoVerificationDecision.ReasonCode.MATCHED;
        }
        if (!decision.objectMatched() && !decision.gestureMatched()) {
            return decision.reasonCode() == PhotoVerificationDecision.ReasonCode.BOTH_MISSING;
        }
        return decision.objectMatched()
                ? decision.reasonCode() == PhotoVerificationDecision.ReasonCode.GESTURE_MISSING
                : decision.reasonCode() == PhotoVerificationDecision.ReasonCode.OBJECT_MISSING;
    }

    private boolean requiredBoolean(JsonNode result, String field) {
        JsonNode value = result.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("Missing boolean field: " + field);
        }
        return value.booleanValue();
    }

    private String requiredText(JsonNode result, String field) {
        JsonNode value = result.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Missing text field: " + field);
        }
        return value.textValue();
    }
}
