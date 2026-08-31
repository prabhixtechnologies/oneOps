package com.prabhix.platform.common.mail;

import java.util.UUID;

/**
 * How every module asks for mail. The only thing outside {@code mail} that mail is reached through.
 *
 * <p>Call it inside the transaction doing the work the mail is about. The implementation writes the
 * request down and returns; nothing is rendered, addressed or sent on the caller's thread, so a slow
 * or broken transport cannot slow down or fail the work that asked for it.
 *
 * <p>Calling inside the transaction is the point, not an incidental convenience. Request and reason
 * then commit together: an invitation that exists always has an invitation email queued, and one
 * that rolled back has none. The Spring event this replaces was delivered after the commit, which
 * left a window where the first was true and the second was not.
 */
public interface MailClient {

    /**
     * Queue a send.
     *
     * @return the request id, or the id of the existing request when {@code dedupeKey} has been
     *         seen before — asking twice is one email, and the second caller gets the first's id
     *         rather than an error
     */
    UUID send(MailRequest request);
}
