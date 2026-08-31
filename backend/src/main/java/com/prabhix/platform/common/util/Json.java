package com.prabhix.platform.common.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Shared JSON helpers for jsonb columns stored as String.
 *
 * <p>Was {@code mail.util.MailJson}, and moved here because nothing about it is mail's: it is how
 * every jsonb column in the schema is read and written. Leaving it where it was would have had mail
 * take it along when mail becomes its own service, and the platform side of the seam needs it too.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
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
