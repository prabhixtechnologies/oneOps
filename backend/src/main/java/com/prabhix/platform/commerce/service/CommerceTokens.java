package com.prabhix.platform.commerce.service;

import java.security.SecureRandom;
import java.util.Base64;

public final class CommerceTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private CommerceTokens() {
    }

    public static String opaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
