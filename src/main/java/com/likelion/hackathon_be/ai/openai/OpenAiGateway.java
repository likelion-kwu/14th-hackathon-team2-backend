package com.likelion.hackathon_be.ai.openai;

import java.util.List;

import tools.jackson.databind.JsonNode;

public interface OpenAiGateway {
    boolean isAvailable();

    JsonNode structuredResponse(
            String schemaName,
            String promptVersion,
            String instructions,
            String inputText,
            List<OpenAiImageInput> images,
            JsonNode schema,
            int maxOutputTokens
    );

    byte[] editImage(
            String promptVersion,
            String prompt,
            List<OpenAiImageInput> images,
            OpenAiImageInput mask,
            String size,
            String quality
    );
}
