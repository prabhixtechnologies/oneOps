package com.prabhix.platform.common.util;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Identifier, slug, and token generation. */
public final class Ids {

    /** Excludes I, O, 0, 1 so codes read aloud over a phone are unambiguous. */
    private static final char[] READABLE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASH = Pattern.compile("(^-+)|(-+$)");
    private static final Pattern DIACRITIC = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private Ids() {
    }

    /** URL-safe, high-entropy token for magic links, invites, and API keys. */
    public static String token(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    public static String token() {
        return token(32);
    }

    /** Numeric OTP. Uses {@link SecureRandom} because these gate account access. */
    public static String numericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        }
        return builder.toString();
    }

    /** Human-dictatable code, used for organization join codes. */
    public static String readableCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(READABLE[RANDOM.nextInt(READABLE.length)]);
        }
        return builder.toString();
    }

    /** Lowercase, dash-separated slug with diacritics folded away. */
    public static String slug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = DIACRITIC.matcher(normalized).replaceAll("");
        normalized = normalized.toLowerCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("-");
        normalized = EDGE_DASH.matcher(normalized).replaceAll("");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
