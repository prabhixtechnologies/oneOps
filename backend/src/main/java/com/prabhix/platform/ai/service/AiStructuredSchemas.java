package com.prabhix.platform.ai.service;

import com.prabhix.platform.ai.provider.model.AiStructuredOutput;

public final class AiStructuredSchemas {

    private AiStructuredSchemas() {
    }

    public static AiStructuredOutput mailTriage() {
        return new AiStructuredOutput("""
                {
                  "type": "object",
                  "properties": {
                    "suggestedTags": { "type": "array", "items": { "type": "string" } },
                    "priority": { "type": "string", "enum": ["LOW", "NORMAL", "HIGH", "URGENT"] },
                    "intent": { "type": "string" },
                    "confidence": { "type": "number" }
                  },
                  "required": ["suggestedTags", "priority", "intent"]
                }
                """, null);
    }

    public static AiStructuredOutput chatSentiment() {
        return new AiStructuredOutput("""
                {
                  "type": "object",
                  "properties": {
                    "sentiment": { "type": "string", "enum": ["positive", "neutral", "negative", "frustrated"] },
                    "urgency": { "type": "string", "enum": ["low", "medium", "high", "critical"] },
                    "summary": { "type": "string" }
                  },
                  "required": ["sentiment", "urgency", "summary"]
                }
                """, null);
    }

    public static AiStructuredOutput leadScore() {
        return new AiStructuredOutput("""
                {
                  "type": "object",
                  "properties": {
                    "score": { "type": "integer", "minimum": 1, "maximum": 100 },
                    "priority": { "type": "string", "enum": ["low", "medium", "high"] },
                    "summary": { "type": "string" },
                    "suggestedActions": { "type": "array", "items": { "type": "string" } }
                  },
                  "required": ["score", "priority", "summary", "suggestedActions"]
                }
                """, null);
    }
}
