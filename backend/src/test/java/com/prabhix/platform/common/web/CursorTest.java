package com.prabhix.platform.common.web;

import com.prabhix.platform.common.error.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CursorTest {

    @Test
    void roundTripsThroughEncoding() {
        Instant timestamp = Instant.parse("2026-08-27T21:06:09.229260Z");
        UUID id = UUID.randomUUID();

        Cursor decoded = Cursor.decode(Cursor.of(timestamp, id).encode());

        assertEquals(timestamp, decoded.timestamp());
        assertEquals(id, decoded.id());
    }

    @Test
    void decodesBlankAsFirstPage() {
        assertNull(Cursor.decode(null));
        assertNull(Cursor.decode(""));
        assertNull(Cursor.decode("   "));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(ApiException.class, () -> Cursor.decode("not-a-cursor"));
    }

    /**
     * Every paged list orders by timestamp descending and walks it with {@code < cursorAt}.
     * An opening cursor that sorted before the rows would return an empty first page, which
     * is exactly the failure this guards against.
     */
    @Test
    void openingCursorSortsAfterEveryPlausibleRow() {
        Cursor beginning = Cursor.beginning();
        Instant farOutButReal = Instant.now().plus(365L * 100L, ChronoUnit.DAYS);

        assertTrue(beginning.timestamp().isAfter(Instant.now()));
        assertTrue(beginning.timestamp().isAfter(farOutButReal));
    }

    /** A timestamp beyond what {@code timestamptz} can hold would fail on bind. */
    @Test
    void openingCursorStaysWithinPostgresTimestampRange() {
        assertTrue(Cursor.beginning().timestamp().isBefore(Instant.parse("+10000-01-01T00:00:00Z")));
    }

    @Test
    void openingCursorSurvivesEncodeDecode() {
        Cursor decoded = Cursor.decode(Cursor.beginning().encode());

        assertEquals(Cursor.beginning().timestamp(), decoded.timestamp());
        assertEquals(Cursor.beginning().id(), decoded.id());
    }
}
