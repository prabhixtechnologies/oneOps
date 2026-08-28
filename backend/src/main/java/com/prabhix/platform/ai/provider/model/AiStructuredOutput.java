package com.prabhix.platform.ai.provider.model;

import java.util.Map;

public record AiStructuredOutput(String jsonSchema, Map<String, Object> schema) {
}
