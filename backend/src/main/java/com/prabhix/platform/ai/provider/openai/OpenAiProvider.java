package com.prabhix.platform.ai.provider.openai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OpenAiProvider implements AiProvider {

    private final AiProperties.ProviderConfig config;
    private final ResilientProviderHttpClient http;
    private final ObjectMapper objectMapper;

    public OpenAiProvider(AiProperties.ProviderConfig config,
                          ResilientProviderHttpClient http,
                          ObjectMapper objectMapper) {
        this.config = config;
        this.http = http;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiProviderId id() {
        return AiProviderId.OPENAI;
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
        ProviderHttpResponse response = http.execute(
                ProviderHttpRequest.post(baseUrl() + "/chat/completions", buildPayload(request, false),
                        "Authorization", "Bearer " + config.apiKey()));
        if (!response.isSuccess()) {
            if (isOpenAiContentFilter(response)) {
                throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by OpenAI");
            }
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "OpenAI request failed");
        }
        JsonNode root = http.parseJson(response.body());
        JsonNode choice = root.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText("stop");
        if ("content_filter".equals(finishReason)) {
            throw ApiException.of(ErrorCode.AI_CONTENT_BLOCKED, "Content blocked by OpenAI");
        }
        String text = choice.path("message").path("content").asText("");
        return new AiCompletionResponse(text, parseUsage(root.path("usage")), finishReason);
    }

    @Override
    public void streamComplete(AiCompletionRequest request, Consumer<AiStreamChunk> consumer) {
        requireConfigured();
        ProviderHttpResponse response = http.execute(
                ProviderHttpRequest.post(baseUrl() + "/chat/completions", buildPayload(request, true),
                        "Authorization", "Bearer " + config.apiKey()));
        if (!response.isSuccess()) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "OpenAI streaming request failed");
        }
        AiTokenUsage lastUsage = AiTokenUsage.empty();
        for (String line : response.body().split("\n")) {
            if (!line.startsWith("data: ")) {
                continue;
            }
            String json = line.substring(6).trim();
            if ("[DONE]".equals(json)) {
                consumer.accept(new AiStreamChunk("", true, lastUsage));
                return;
            }
            JsonNode root = http.parseJson(json);
            JsonNode delta = root.path("choices").path(0).path("delta");
            String text = delta.path("content").asText("");
            if (!text.isEmpty()) {
                consumer.accept(new AiStreamChunk(text, false, AiTokenUsage.empty()));
            }
            if (root.has("usage")) {
                lastUsage = parseUsage(root.path("usage"));
            }
        }
        consumer.accept(new AiStreamChunk("", true, lastUsage));
    }

    @Override
    public AiEmbeddingResponse embed(AiEmbeddingRequest request) {
        requireConfigured();
        String model = request.model() != null ? request.model() : config.embeddingModel();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);
        payload.put("input", request.text());
        try {
            ProviderHttpResponse response = http.execute(
                    ProviderHttpRequest.post(baseUrl() + "/embeddings",
                            objectMapper.writeValueAsString(payload),
                            "Authorization", "Bearer " + config.apiKey()));
            if (!response.isSuccess()) {
                throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "OpenAI embedding request failed");
            }
            JsonNode root = http.parseJson(response.body());
            ArrayNode embedding = (ArrayNode) root.path("data").path(0).path("embedding");
            List<Double> vector = new ArrayList<>();
            embedding.forEach(n -> vector.add(n.asDouble()));
            return new AiEmbeddingResponse(vector, parseUsage(root.path("usage")));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "OpenAI embedding failed", ex);
        }
    }

    String buildPayload(AiCompletionRequest request, boolean stream) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", resolveModel(request.model()));
            ArrayNode messages = root.putArray("messages");
            for (AiMessage message : request.messages()) {
                ObjectNode node = messages.addObject();
                node.put("role", message.role().name().toLowerCase());
                node.put("content", message.content());
            }
            if (request.temperature() != null) {
                root.put("temperature", request.temperature());
            }
            if (request.maxOutputTokens() != null) {
                root.put("max_tokens", request.maxOutputTokens());
            }
            if (stream) {
                root.put("stream", true);
            }
            if (request.structuredOutput() != null) {
                ObjectNode responseFormat = root.putObject("response_format");
                responseFormat.put("type", "json_schema");
                ObjectNode jsonSchema = responseFormat.putObject("json_schema");
                jsonSchema.put("name", "structured_output");
                jsonSchema.put("strict", true);
                if (request.structuredOutput().jsonSchema() != null) {
                    jsonSchema.set("schema", objectMapper.readTree(request.structuredOutput().jsonSchema()));
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.AI_PROVIDER_ERROR, "Failed to build OpenAI payload", ex);
        }
    }

    static AiTokenUsage parseUsage(JsonNode usage) {
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);
        int total = usage.path("total_tokens").asInt(prompt + completion);
        return new AiTokenUsage(prompt, completion, total);
    }

    static boolean isOpenAiContentFilter(ProviderHttpResponse response) {
        return response.body().contains("content_filter")
                || ResilientProviderHttpClient.isContentBlocked(response);
    }

    private void requireConfigured() {
        if (!configured()) {
            throw ApiException.of(ErrorCode.AI_NOT_CONFIGURED, "OpenAI API key is not configured");
        }
    }

    private String resolveModel(String requested) {
        return requested != null && !requested.isBlank() ? requested : config.chatModel();
    }

    private String baseUrl() {
        return config.baseUrl().replaceAll("/$", "");
    }
}
