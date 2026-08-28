package com.prabhix.platform.mail.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MailSubjectUtil {

    private static final Pattern RE_FWD = Pattern.compile(
            "^(\\s*(re|fwd|fw)\\s*:\\s*)+", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREAD_TOKEN = Pattern.compile(
            "\\[#([A-Z0-9-]+)\\]", Pattern.CASE_INSENSITIVE);

    private MailSubjectUtil() {
    }

    public static String normalize(String subject) {
        if (subject == null) {
            return "";
        }
        String stripped = RE_FWD.matcher(subject.trim()).replaceAll("").trim();
        stripped = THREAD_TOKEN.matcher(stripped).replaceAll("").trim();
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String injectToken(String subject, String prefix, String referenceKey) {
        String token = "[#" + prefix + "-" + referenceKey + "]";
        if (subject == null || subject.isBlank()) {
            return token;
        }
        if (subject.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
            return subject;
        }
        return subject + " " + token;
    }

    public static String extractReferenceKey(String subject, String prefix) {
        if (subject == null) {
            return null;
        }
        var matcher = Pattern.compile(
                "\\[#" + Pattern.quote(prefix) + "-([A-Z0-9]+)\\]",
                Pattern.CASE_INSENSITIVE).matcher(subject);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }
}
