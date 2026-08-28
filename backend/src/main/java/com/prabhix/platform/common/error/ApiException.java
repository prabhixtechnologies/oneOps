package com.prabhix.platform.common.error;

import lombok.Getter;

import java.util.Map;

/**
 * The only exception feature code should throw for an expected failure.
 *
 * <p>Carries a stable {@link ErrorCode} plus an optional per-field map so a single throw can
 * drive both a toast and inline form errors on the client.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    private ApiException(ErrorCode code, String message, Map<String, String> fieldErrors, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message, null, null);
    }

    public static ApiException of(ErrorCode code, String message, Throwable cause) {
        return new ApiException(code, message, null, cause);
    }

    public static ApiException withFields(ErrorCode code, String message, Map<String, String> fieldErrors) {
        return new ApiException(code, message, fieldErrors, null);
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " was not found", null, null);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message, null, null);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message, null, null);
    }

    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, message, null, null);
    }
}
