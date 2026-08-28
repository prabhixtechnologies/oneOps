package com.prabhix.platform.push.provider;

import com.prabhix.platform.push.domain.PushEnums;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface PushProvider {

    String providerId();

    boolean configured();

    SendResult send(SendRequest request);

    record SendRequest(
            UUID organizationId,
            UUID userId,
            PushEnums.Platform platform,
            String deviceToken,
            String notificationType,
            Map<String, Object> payload) {
    }

    record SendResult(boolean success, String providerMessageId, String error, Set<String> invalidTokens) {
        public static SendResult ok(String messageId) {
            return new SendResult(true, messageId, null, Set.of());
        }

        public static SendResult failed(String error) {
            return new SendResult(false, null, error, Set.of());
        }

        public static SendResult invalidToken(String token) {
            return new SendResult(false, null, "Invalid token", Set.of(token));
        }
    }
}
