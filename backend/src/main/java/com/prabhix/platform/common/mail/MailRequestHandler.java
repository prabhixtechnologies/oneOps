package com.prabhix.platform.common.mail;

import java.util.UUID;

/**
 * The far side of the seam: what actually accepts a queued request.
 *
 * <p>Implemented today by the mail module, in the same process. When mail becomes its own service
 * the implementation becomes an HTTP call to it, and nothing on this side changes — not the callers,
 * not {@link MailClient}, not the table the relay drains. That is the whole reason the relay hands
 * rows to an interface rather than calling mail directly.
 *
 * <p>Separate from {@link MailClient} because they are opposite ends of the queue. Callers must not
 * reach this: it does the work {@link MailClient} exists to defer.
 */
public interface MailRequestHandler {

    /**
     * Accept a request for delivery.
     *
     * <p>Must be idempotent on {@code dedupeKey}. The relay retries, and a retry after a delivery
     * whose acknowledgement was lost has to be a no-op rather than a second email.
     *
     * @return the identifier of the accepted message, recorded against the request so a delivery
     *         can be traced back to what asked for it
     * @throws RuntimeException to have the request retried with backoff
     */
    UUID accept(MailRequest request);
}
