package com.prabhix.platform.push.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public final class PushJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PushJson() {
    }

    public static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("Could not serialize push payload", ex);
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
            throw new IllegalArgumentException("Could not parse push payload", ex);
        }
    }
}
