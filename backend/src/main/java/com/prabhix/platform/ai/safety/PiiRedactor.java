package com.prabhix.platform.ai.safety;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile(
            "\\b(?:\\+91[- ]?)?[6-9]\\d{9}\\b|\\b\\+?\\d{1,3}[-.\\s]?\\(?\\d{2,4}\\)?[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b");
    private static final Pattern PAN = Pattern.compile(
            "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AADHAAR = Pattern.compile(
            "(?<!\\d)\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?![\\s-]?\\d)");
    private static final Pattern CARD = Pattern.compile(
            "\\b\\d{13,19}\\b");

    public record RedactionResult(String text, boolean redacted) {
    }

    public RedactionResult redact(String input) {
        if (input == null || input.isBlank()) {
            return new RedactionResult(input, false);
        }
        String result = input;
        result = EMAIL.matcher(result).replaceAll("[REDACTED_EMAIL]");
        result = PAN.matcher(result).replaceAll("[REDACTED_PAN]");
        result = AADHAAR.matcher(result).replaceAll("[REDACTED_AADHAAR]");
        result = CARD.matcher(result).replaceAll("[REDACTED_CARD]");
        result = PHONE.matcher(result).replaceAll("[REDACTED_PHONE]");
        return new RedactionResult(result, !result.equals(input));
    }
}
