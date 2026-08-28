package com.prabhix.platform.common.error;

import com.prabhix.platform.observability.context.CorrelationContext;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into an {@link ApiError}.
 *
 * <p>Unexpected exceptions are logged with a generated trace id and returned as a generic
 * {@code INTERNAL_ERROR}: the client gets the trace id to quote in a support request, and
 * nothing about our internals leaks into the response.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final StructuredEventLogger eventLogger;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        String traceId = traceId();
        HttpStatus status = ex.getCode().status();

        // 5xx means we did something wrong, so keep the stack trace. 4xx is the caller's
        // problem and would only pollute the logs at scale.
        if (status.is5xxServerError()) {
            log.error("[{}] {} at {}", traceId, ex.getCode(), request.getRequestURI(), ex);
            eventLogger.log(LogEventCode.PLATFORM_UNHANDLED_ERROR, java.util.Map.of(
                    "code", ex.getCode().name(),
                    "path", request.getRequestURI()), false);
        } else if (ex.getCode() == ErrorCode.CROSS_TENANT_ACCESS) {
            eventLogger.log(LogEventCode.SECURITY_CROSS_TENANT_BLOCKED, java.util.Map.of(
                    "path", request.getRequestURI(),
                    "message", ex.getMessage()));
        } else {
            log.debug("[{}] {} at {}: {}", traceId, ex.getCode(), request.getRequestURI(), ex.getMessage());
        }

        return ResponseEntity.status(status)
                .body(ApiError.of(ex.getCode(), ex.getMessage(), ex.getFieldErrors(), traceId,
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(),
                    error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return respond(ErrorCode.VALIDATION_FAILED, "Some fields need attention", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                             HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return respond(ErrorCode.VALIDATION_FAILED, "Some fields need attention", fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception ex, HttpServletRequest request) {
        log.debug("Malformed request at {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.MALFORMED_REQUEST, "The request could not be read", null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex,
                                                        HttpServletRequest request) {
        log.debug("Authentication failed at {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.UNAUTHENTICATED, "Authentication is required", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                      HttpServletRequest request) {
        log.debug("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.PERMISSION_DENIED,
                "You do not have permission to perform this action", null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleStale(OptimisticLockingFailureException ex,
                                               HttpServletRequest request) {
        log.debug("Optimistic lock clash at {}", request.getRequestURI());
        return respond(ErrorCode.STALE_RESOURCE,
                "Someone else changed this while you were editing. Reload and try again.", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                   HttpServletRequest request) {
        String traceId = traceId();
        // The root cause names the violated constraint, which is useful to us but must not
        // reach the client, since it exposes schema details.
        log.warn("[{}] Data integrity violation at {}: {}", traceId, request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(ApiError.of(ErrorCode.CONFLICT,
                        "That change conflicts with existing data", traceId, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                        HttpServletRequest request) {
        return respond(ErrorCode.LIMIT_EXCEEDED, "That file is too large", null, request);
    }

    // NoHandlerFoundException covers unmapped paths; NoResourceFoundException is what Spring
    // Boot 3 actually throws once the request has fallen through to static resource handling.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.NOT_FOUND, "No such endpoint", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                          HttpServletRequest request) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED,
                "That endpoint does not accept " + request.getMethod(), null, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                              HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "That content type is not supported by this endpoint", null, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = traceId();
        log.error("[{}] Unhandled exception at {}", traceId, request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR,
                        "Something went wrong on our side. Quote reference " + traceId + " if it persists.",
                        traceId, request.getRequestURI()));
    }

    private ResponseEntity<ApiError> respond(ErrorCode code,
                                             String message,
                                             Map<String, String> fields,
                                             HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ApiError.of(code, message, fields, traceId(), request.getRequestURI()));
    }

    private String traceId() {
        String correlation = CorrelationContext.currentOrNew();
        return correlation.substring(0, Math.min(8, correlation.length()));
    }
}
