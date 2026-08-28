package com.prabhix.platform.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Offset-paginated envelope.
 *
 * <p>Only for small, bounded collections such as roles or plans. Anything that grows with
 * tenant size must use {@link CursorPage} instead, because {@code OFFSET n} makes Postgres
 * walk and discard n rows on every request.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious());
    }

    public static <T> PageResponse<T> of(List<T> items) {
        return new PageResponse<>(items, 0, items.size(), items.size(), 1, false, false);
    }
}
