package com.prabhix.platform.common.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes and decodes the opaque list cursor.
 *
 * <p>The sort key for every large list is {@code (created_at desc, id desc)}. Timestamp
 * alone is not unique enough — two rows written in the same microsecond would make a page
 * boundary skip or repeat a row — so the id is the tiebreaker and travels in the cursor too.
 *
 * <p>Encoding is Base64URL of {@code <epochMicros>:<uuid>}. It is obfuscation, not security:
 * a cursor reveals only a timestamp and an id the caller already had, and every query it
 * feeds is still tenant-filtered and permission-checked.
 */
public record Cursor(Instant timestamp, UUID id) {

    private static final String SEPARATOR = ":";

    public static Cursor of(Instant timestamp, UUID id) {
        return new Cursor(timestamp, id);
    }

    public String encode() {
        long micros = timestamp.getEpochSecond() * 1_000_000L + timestamp.getNano() / 1_000L;
        String raw = micros + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @return the decoded cursor, or {@code null} when {@code encoded} is blank (first page). */
    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int split = raw.indexOf(SEPARATOR);
            if (split < 0) {
                throw new IllegalArgumentException("missing separator");
            }
            long micros = Long.parseLong(raw.substring(0, split));
            UUID id = UUID.fromString(raw.substring(split + 1));
            Instant timestamp = Instant.ofEpochSecond(
                    Math.floorDiv(micros, 1_000_000L),
                    Math.floorMod(micros, 1_000_000L) * 1_000L);
            return new Cursor(timestamp, id);
        } catch (RuntimeException ex) {
            throw ApiException.of(ErrorCode.INVALID_CURSOR,
                    "That pagination cursor is not valid. Start from the first page.");
        }
    }

    /**
     * A sentinel for the first page, so keyset predicates can be written once with no
     * {@code null} branch.
     *
     * <p>Every paged list here sorts {@code (timestamp desc, id desc)} and walks it with
     * {@code timestamp < :cursorAt}. The opening cursor therefore has to sort <em>after</em>
     * every real row, not before: an epoch sentinel would make {@code timestamp < 1970}
     * false for all of them and return an empty first page.
     *
     * <p>The timestamp is a far-future value rather than {@link Instant#MAX} because
     * {@code timestamptz} tops out in 294276 AD and would overflow on bind. The id is all
     * ones, which Postgres orders last for {@code uuid} (bytewise, unsigned). Note that
     * {@link UUID#compareTo} would rank the same value first, since it compares the bits as
     * signed longs — this constant is only ever a query parameter, never sorted in Java.
     */
    public static Cursor beginning() {
        return new Cursor(FAR_FUTURE, new UUID(-1L, -1L));
    }

    private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T23:59:59.999999Z");
}
