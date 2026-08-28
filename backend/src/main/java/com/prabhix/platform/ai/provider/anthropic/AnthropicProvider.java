package com.prabhix.platform.ai.provider.anthropic;

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

import java.util.function.Consumer;

public class AnthropicProvider implements AiProvider {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AiProperties.ProviderConfig config;
    private final ResilientProviderHttpClient http;
    private final ObjectMapper objectMapper;

    public AnthropicProvider(AiProperties.ProviderConfig config,
                             ResilientProviderHttpClient http,
                             ObjectMapper objectMapper) {
        this.config = config;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderId id() {
        return AiProviderId.ANTHROPIC;
    }

    @Override
    public AiCapabilities capabilities() {
        return new AiCapabilities(true, false, false);
    }

    @Override
    public boolean configured() {
        return config.configured();
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
        requireConfigured();
        ProviderHttpRequest httpRequest = buildRequest(request, false);
        ProviderHttpResponse response = http.execute(httpRequest);
        if (!response.isSuccess()) {
            if (isAnthropicBlocked(response)) {
                throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Anthropic");
            }
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Anthropic request failed");
        }
        JsonNode root = http.parseJson(response.body());
        if ("end_turn".equals(root.path("stop_reason").asText()) && root.path("content").isEmpty()) {
            // ok
        }
        String stopReason = root.path("stop_reason").asText("");
        if ("content_filter".equals(stopReason)) {
            throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by Anthropic");
        }
        StringBuilder text = new StringBuilder();
        root.path("content").forEach(block -> {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText(""));
            }
        });
        return new AiCompletionResponse(text.toString(), parseUsage(root.path("usage")), stopReason);
    }

    @Override
    public void streamComplete(AiCompletionRequest request, Consumer<AiStreamChunk> consumer) {
        requireConfigured();
        ProviderHttpResponse response = http.execute(buildRequest(request, true));
        if (!response.isSuccess()) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Anthropic streaming request failed");
        }
        AiTokenUsage lastUsage = AiTokenUsage.empty();
        for (String line : response.body().split("\n")) {
            if (!line.startsWith("data: ")) {
                continue;
            }
            String json = line.substring(6).trim();
            if (json.isEmpty()) {
                continue;
            }
            JsonNode root = http.parseJson(json);
            if (root.has("usage")) {
                lastUsage = parseUsage(root.path("usage"));
            }
            JsonNode delta = root.path("delta");
            if (delta.has("text")) {
                consumer.accept(new AiStreamChunk(delta.path("text").asText(""), false, AiTokenUsage.empty()));
            }
            if ("message_stop".equals(root.path("type").asText())) {
                consumer.accept(new AiStreamChunk("", true, lastUsage));
            }
        }
        consumer.accept(new AiStreamChunk("", true, lastUsage));
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        throw ApiException.of(ErrorCode.AI_MODEL_NOT_SUPPORTED,
                "Anthropic provider does not support embeddings");
    }

    ProviderHttpRequest buildRequest(AiCompletionRequest request, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", resolveModel(request.model()));
            root.put("max_tokens", request.maxOutputTokens() != null ? request.maxOutputTokens() : 2048);
            if (request.temperature() != null) {
                root.put("temperature", request.temperature());
            }
            if (stream) {
                root.put("stream", true);
            }
            String systemText = null;
            ArrayNode messages = root.putArray("messages");
            for (AiMessage message : request.messages()) {
                if (message.role() == AiRole.SYSTEM) {
                    systemText = message.content();
                    continue;
                }
                ObjectNode node = messages.addObject();
                node.put("role", message.role() == AiRole.ASSISTANT ? "assistant" : "user");
                node.put("content", message.content());
            }
            if (systemText != null) {
                root.put("system", systemText);
            }
            return new ProviderHttpRequest(
                    org.springframework.http.HttpMethod.POST,
                    baseUrl() + "/messages",
                    java.util.Map.of(
                            "x-api-key", config.apiKey(),
                            "anthropic-version", ANTHROPIC_VERSION,
                            "Content-Type", "application/json"),
                    objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Failed to build Anthropic payload", ex);
        }
    }

    static AiTokenUsage parseUsage(JsonNode usage) {
        int prompt = usage.path("input_tokens").asInt(0);
        int completion = usage.path("output_tokens").asInt(0);
        return new AiTokenUsage(prompt, completion, prompt + completion);
    }

    static boolean isAnthropicBlocked(ProviderHttpResponse response) {
        return response.body().contains("content_filter")
                || ResilientProviderHttpClient.isContentBlocked(response);
    }

    private void requireConfigured() {
        if (!configured()) {
            throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED, "Anthropic API key is not configured");
        }
    }

    private String resolveModel(String requested) {
        return requested != null && !requested.isBlank() ? requested : config.chatModel();
    }

    private String baseUrl() {
        return config.baseUrl().replaceAll("/$", "");
    }
}
