package com.prabhix.platform.mail.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/** Shared JSON helpers for jsonb columns stored as String. */
public final class MailJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MailJson() {
    }

    public static String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Could not serialise value to JSON", ex);
        }
    }

    public static Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON map", ex);
        }
    }

    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON array", ex);
        }
    }

    public static List<Map<String, Object>> parseObjectList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Invalid JSON array", ex);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
