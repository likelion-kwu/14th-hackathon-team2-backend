package com.likelion.hackathon_be.ai.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class RestOpenAiGateway implements OpenAiGateway {
    private static final int MAX_ATTEMPTS = 2;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient responsesClient;
    private final RestClient imagesClient;

    public RestOpenAiGateway(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.responsesClient = client(properties.responseTimeout());
        this.imagesClient = client(properties.imageTimeout());
    }

    @Override
    public boolean isAvailable() {
        return properties.configured();
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
        ensureConfigured();
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", inputText));
        for (OpenAiImageInput image : images) {
            content.add(Map.of(
                    "type", "input_image",
                    "image_url", image.dataUrl(),
                    "detail", image.detail()
            ));
        }

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("schema", schema);
        format.put("strict", true);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.responsesModel());
        body.put("instructions", instructions);
        body.put("input", List.of(Map.of("role", "user", "content", content)));
        body.put("text", Map.of("format", format));
        body.put("reasoning", Map.of("effort", "none"));
        body.put("max_output_tokens", maxOutputTokens);
        body.put("store", false);

        JsonNode response = execute(
                "responses",
                properties.responsesModel(),
                promptVersion,
                responsesClient,
                "/v1/responses",
                body,
                MediaType.APPLICATION_JSON
        );
        ensureCompleted(response);
        JsonNode refusal = response.findValue("refusal");
        if (refusal != null && !refusal.isNull() && !refusal.asText().isBlank()) {
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.REFUSED, "OpenAI refused the request");
        }
        JsonNode text = firstOutputText(response);
        if (text == null) {
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.INVALID_RESPONSE, "Missing output_text");
        }
        try {
            return objectMapper.readTree(text.asText());
        } catch (Exception exception) {
            throw new OpenAiGatewayException(
                    OpenAiGatewayException.Kind.INVALID_RESPONSE,
                    "Invalid structured response JSON",
                    exception
            );
        }
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
        ensureConfigured();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", properties.imageModel());
        body.add("prompt", prompt);
        int inputIndex = 0;
        for (OpenAiImageInput image : images) {
            body.add(
                    "image[]",
                    imagePart(image, "input-" + inputIndex++ + extension(image.mediaType()))
            );
        }
        if (mask != null) {
            body.add("mask", imagePart(mask, "mask" + extension(mask.mediaType())));
        }
        body.add("size", size);
        body.add("quality", quality);
        body.add("output_format", "png");
        body.add("n", "1");

        JsonNode response = execute(
                "images.edit",
                properties.imageModel(),
                promptVersion,
                imagesClient,
                "/v1/images/edits",
                body,
                MediaType.MULTIPART_FORM_DATA
        );
        JsonNode image = response.path("data").path(0).path("b64_json");
        if (!image.isTextual()) {
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.INVALID_RESPONSE, "Missing generated image");
        }
        try {
            return Base64.getDecoder().decode(image.asText());
        } catch (IllegalArgumentException exception) {
            throw new OpenAiGatewayException(
                    OpenAiGatewayException.Kind.INVALID_RESPONSE,
                    "Invalid generated image encoding",
                    exception
            );
        }
    }

    private RestClient client(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + nullToEmpty(properties.apiKey()))
                .build();
    }

    private JsonNode execute(
            String operation,
            String model,
            String promptVersion,
            RestClient client,
            String uri,
            Object body,
            MediaType contentType
    ) {
        long started = System.nanoTime();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<JsonNode> entity = client.post()
                        .uri(uri)
                        .contentType(contentType)
                        .body(body)
                        .retrieve()
                        .toEntity(JsonNode.class);
                JsonNode response = entity.getBody();
                if (response == null) {
                    throw new OpenAiGatewayException(OpenAiGatewayException.Kind.INVALID_RESPONSE, "Empty response");
                }
                log.info(
                        "ai_call operation={} model={} promptVersion={} requestId={} elapsedMs={} inputTokens={} outputTokens={} attempt={}",
                        operation,
                        model,
                        promptVersion,
                        entity.getHeaders().getFirst("x-request-id"),
                        elapsedMillis(started),
                        response.path("usage").path("input_tokens").asInt(0),
                        response.path("usage").path("output_tokens").asInt(0),
                        attempt
                );
                return response;
            } catch (RestClientResponseException exception) {
                if (!isTransient(exception.getStatusCode()) || attempt == MAX_ATTEMPTS) {
                    log.warn(
                            "ai_call_failed operation={} model={} promptVersion={} status={} elapsedMs={} requestId={}",
                            operation,
                            model,
                            promptVersion,
                            exception.getStatusCode().value(),
                            elapsedMillis(started),
                            exception.getResponseHeaders() == null
                                    ? null
                                    : exception.getResponseHeaders().getFirst("x-request-id")
                    );
                    throw new OpenAiGatewayException(
                            OpenAiGatewayException.Kind.UNAVAILABLE,
                            "OpenAI request failed",
                            exception
                    );
                }
                backoff();
            } catch (ResourceAccessException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn(
                            "ai_call_failed operation={} model={} promptVersion={} status=NETWORK_ERROR elapsedMs={}",
                            operation,
                            model,
                            promptVersion,
                            elapsedMillis(started)
                    );
                    throw new OpenAiGatewayException(
                            OpenAiGatewayException.Kind.UNAVAILABLE,
                            "OpenAI request timed out",
                            exception
                    );
                }
                backoff();
            } catch (RestClientException exception) {
                log.warn(
                        "ai_call_failed operation={} model={} promptVersion={} status=INVALID_RESPONSE elapsedMs={}",
                        operation,
                        model,
                        promptVersion,
                        elapsedMillis(started)
                );
                throw new OpenAiGatewayException(
                        OpenAiGatewayException.Kind.INVALID_RESPONSE,
                        "OpenAI response could not be decoded",
                        exception
                );
            }
        }
        throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "OpenAI request failed");
    }

    private JsonNode firstOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText()) && content.path("text").isTextual()) {
                    return content.path("text");
                }
            }
        }
        return null;
    }

    private void ensureCompleted(JsonNode response) {
        String status = response.path("status").asText("");
        if ("incomplete".equals(status)) {
            String reason = response.path("incomplete_details").path("reason").asText("");
            OpenAiGatewayException.Kind kind = "content_filter".equals(reason)
                    ? OpenAiGatewayException.Kind.REFUSED
                    : OpenAiGatewayException.Kind.INVALID_RESPONSE;
            throw new OpenAiGatewayException(kind, "OpenAI returned an incomplete response");
        }
        if ("failed".equals(status)
                || (!response.path("error").isMissingNode() && !response.path("error").isNull())) {
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "OpenAI response failed");
        }
    }

    private void ensureConfigured() {
        if (!isAvailable()) {
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "OPENAI_API_KEY is not configured");
        }
    }

    private boolean isTransient(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private void backoff() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiGatewayException(OpenAiGatewayException.Kind.UNAVAILABLE, "Retry interrupted", exception);
        }
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String extension(String mediaType) {
        return "image/jpeg".equalsIgnoreCase(mediaType) ? ".jpg" : ".png";
    }

    private HttpEntity<NamedByteArrayResource> imagePart(OpenAiImageInput image, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(image.mediaType()));
        return new HttpEntity<>(new NamedByteArrayResource(image.bytes(), filename), headers);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
