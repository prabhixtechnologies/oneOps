package com.prabhix.platform.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.function.Function;

/**
 * Keyset-paginated envelope. The default for every list that scales with tenant size.
 *
 * <p>{@code nextCursor} is opaque to clients: they echo it back and must not parse it. That
 * keeps us free to change the sort key later without a breaking API change.
 *
 * <p>Serialization is pinned to ALWAYS because the application-wide default is
 * {@code non_null}, which would drop {@code nextCursor} from the last page entirely. This is a
 * protocol field that clients branch on, and the OpenAPI schema declares it as a nullable string,
 * so a missing key contradicts the published contract and breaks strict client validators.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {

    public static <T> CursorPage<T> empty() {
        return new CursorPage<>(List.of(), null, false);
    }

    /**
     * Builds a page from a batch fetched with {@code limit + 1} rows. The extra row is the
     * lookahead that tells us whether more data exists without running a count query.
     *
     * @param fetched     rows returned by the query, at most {@code limit + 1} of them
     * @param limit       page size the caller asked for
     * @param mapper      entity to DTO
     * @param cursorOf    builds the cursor from the last entity actually returned
     */
    public static <E, T> CursorPage<T> of(List<E> fetched,
                                          int limit,
                                          Function<E, T> mapper,
                                          Function<E, String> cursorOf) {
        boolean hasMore = fetched.size() > limit;
        List<E> window = hasMore ? fetched.subList(0, limit) : fetched;

        return new CursorPage<>(
                window.stream().map(mapper).toList(),
                hasMore && !window.isEmpty() ? cursorOf.apply(window.get(window.size() - 1)) : null,
                hasMore);
    }
}
