package com.prabhix.platform.ai.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.prabhix.platform.ai.config.AiProperties;
import com.prabhix.platform.ai.provider.AiCapabilities;
import com.prabhix.platform.ai.provider.AiProvider;
import com.prabhix.platform.ai.provider.AiProviderId;
import com.prabhix.platform.ai.provider.http.ProviderHttpRequest;
import com.prabhix.platform.ai.provider.http.ProviderHttpResponse;
import com.prabhix.platform.ai.provider.http.ResilientProviderHttpClient;
import com.prabhix.platform.ai.provider.model.AiCompletionRequest;
import com.prabhix.platform.ai.provider.model.AiCompletionResponse;
import com.prabhix.platform.ai.provider.model.AiEmbeddingRequest;
import com.prabhix.platform.ai.provider.model.AiEmbeddingResponse;
import com.prabhix.platform.ai.provider.model.AiMessage;
import com.prabhix.platform.ai.provider.model.AiRole;
import com.prabhix.platform.ai.provider.model.AiStreamChunk;
import com.prabhix.platform.ai.provider.model.AiTokenUsage;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class GeminiProvider implements AiProvider {

    private final AiProperties.ProviderConfig config;
    private final ResilientProviderHttpClient http;
    private final ObjectMapper objectMapper;

    public GeminiProvider(AiProperties.ProviderConfig config,
                          ResilientProviderHttpClient http,
                          ObjectMapper objectMapper) {
        this.config = config;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderId id() {
        return AiProviderId.GEMINI;
    }

    @Override
    public AiCapabilities capabilities() {
        return new AiCapabilities(true, true, true);
    }

    @Override
    public boolean configured() {
        return config.configured();
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
        requireConfigured();
        String model = resolveModel(request.model());
        String url = baseUrl() + "/models/" + model + ":generateContent?key=" + config.apiKey();
        ProviderHttpResponse response = http.execute(
                ProviderHttpRequest.post(url, buildPayload(request, false), "Accept", "application/json"));
        if (!response.isSuccess()) {
            if (isGeminiBlocked(response)) {
                throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Gemini safety filters");
            }
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Gemini request failed");
        }
        JsonNode root = http.parseJson(response.body());
        checkPromptFeedback(root);
        return parseCompletion(root);
    }

    @Override
    public void streamComplete(AiCompletionRequest request, Consumer<AiStreamChunk> consumer) {
        requireConfigured();
        String model = resolveModel(request.model());
        String url = baseUrl() + "/models/" + model + ":streamGenerateContent?key=" + config.apiKey()
                + "&alt=sse";
        ProviderHttpResponse response = http.execute(
                ProviderHttpRequest.post(url, buildPayload(request, true), "Accept", "text/event-stream"));
        if (!response.isSuccess()) {
            if (isGeminiBlocked(response)) {
                throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Gemini safety filters");
            }
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Gemini streaming request failed");
        }
        parseStream(response.body(), consumer);
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        requireConfigured();
        String model = request.model() != null ? request.model() : config.embeddingModel();
        String url = baseUrl() + "/models/" + model + ":embedContent?key=" + config.apiKey();
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode content = payload.putObject("content");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", request.text());
        payload.put("model", "models/" + model);
        try {
            ProviderHttpResponse response = http.execute(
                    ProviderHttpRequest.post(url, objectMapper.writeValueAsString(payload),
                            "Accept", "application/json"));
            if (!response.isSuccess()) {
                throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Gemini embedding request failed");
            }
            JsonNode root = http.parseJson(response.body());
            ArrayNode values = (ArrayNode) root.path("embedding").path("values");
            List<Double> vector = new ArrayList<>();
            values.forEach(n -> vector.add(n.asDouble()));
            return new AiEmbeddingResponse(vector, AiTokenUsage.empty());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Gemini embedding failed", ex);
        }
    }

    String buildPayload(AiCompletionRequest request, boolean stream) throws RuntimeException {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            String systemText = null;
            for (AiMessage message : request.messages()) {
                if (message.role() == AiRole.SYSTEM) {
                    systemText = message.content();
                    continue;
                }
                ObjectNode content = contents.addObject();
                content.put("role", message.role() == AiRole.ASSISTANT ? "model" : "user");
                content.putArray("parts").addObject().put("text", message.content());
            }
            if (systemText != null) {
                root.putObject("systemInstruction").putArray("parts").addObject().put("text", systemText);
            }
            ObjectNode genConfig = root.putObject("generationConfig");
            if (request.maxOutputTokens() != null) {
                genConfig.put("maxOutputTokens", request.maxOutputTokens());
            }
            if (request.temperature() != null) {
                genConfig.put("temperature", request.temperature());
            }
            if (request.structuredOutput() != null && request.structuredOutput().jsonSchema() != null) {
                genConfig.put("responseMimeType", "application/json");
                genConfig.set("responseSchema", objectMapper.readTree(request.structuredOutput().jsonSchema()));
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Failed to build Gemini payload", ex);
        }
    }

    AiCompletionResponse parseCompletion(JsonNode root) {
        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("STOP");
        if ("SAFETY".equalsIgnoreCase(finishReason)) {
            throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Gemini safety filters");
        }
        StringBuilder text = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> text.append(part.path("text").asText("")));
        AiTokenUsage usage = parseUsage(root.path("usageMetadata"));
        return new AiCompletionResponse(text.toString(), usage, finishReason);
    }

    void parseStream(String body, Consumer<AiStreamChunk> consumer) {
        String accumulated = "";
        AiTokenUsage lastUsage = AiTokenUsage.empty();
        for (String line : body.split("\n")) {
            if (!line.startsWith("data: ")) {
                continue;
            }
            String json = line.substring(6).trim();
            if (json.isEmpty()) {
                continue;
            }
            JsonNode root = http.parseJson(json);
            checkPromptFeedback(root);
            JsonNode candidate = root.path("candidates").path(0);
            String delta = candidate.path("content").path("parts").path(0).path("text").asText("");
            if (!delta.isEmpty()) {
                accumulated += delta;
                consumer.accept(new AiStreamChunk(delta, false, AiTokenUsage.empty()));
            }
            lastUsage = parseUsage(root.path("usageMetadata"));
        }
        consumer.accept(new AiStreamChunk("", true, lastUsage.totalTokens() > 0
                ? lastUsage : estimateUsage(accumulated)));
    }

    static AiTokenUsage parseUsage(JsonNode usage) {
        int prompt = usage.path("promptTokenCount").asInt(0);
        int completion = usage.path("candidatesTokenCount").asInt(0);
        int total = usage.path("totalTokenCount").asInt(prompt + completion);
        return new AiTokenUsage(prompt, completion, total);
    }

    static AiTokenUsage estimateUsage(String text) {
        int tokens = Math.max(1, text.length() / 4);
        return new AiTokenUsage(0, tokens, tokens);
    }

    static void checkPromptFeedback(JsonNode root) {
        String blockReason = root.path("promptFeedback").path("blockReason").asText("");
        if (!blockReason.isBlank()) {
            throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Gemini safety filters");
        }
    }

    static boolean isGeminiBlocked(ProviderHttpResponse response) {
        return ResilientProviderHttpClient.isContentBlocked(response)
                || response.body().contains("blockReason");
    }

    private void requireConfigured() {
        if (!configured()) {
            throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED, "Gemini API key is not configured");
        }
    }

    private String resolveModel(String requested) {
        return requested != null && !requested.isBlank() ? requested : config.chatModel();
    }

    private String baseUrl() {
        return config.baseUrl().replaceAll("/$", "");
    }
}
