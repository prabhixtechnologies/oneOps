package com.prabhix.platform.observability.taxonomy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEventCodeTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]+(\\.[a-z0-9_]+)+$");

    @Test
    void everyCodeIsUniqueAndWellFormed() {
        long distinct = Arrays.stream(LogEventCode.values())
                .map(LogEventCode::code)
                .distinct()
                .count();
        assertEquals(LogEventCode.values().length, distinct);

        for (LogEventCode code : LogEventCode.values()) {
            assertTrue(CODE_PATTERN.matcher(code.code()).matches(),
                    () -> "Malformed code: " + code.code());
            assertEquals(code, LogEventCode.fromCode(code.code()));
        }
    }
}
