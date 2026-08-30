package com.prabhix.platform.common.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads a mailbox address, declared here so modules that route mail to a configured mailbox do not
 * compile against {@code mail}. The mail module supplies the implementation.
 *
 * <p>Deliberately narrow. Chat's one need was the address behind {@code chat_settings
 * .offline_mailbox_id}, and it met that need by injecting {@code MailboxRepository} and holding a
 * whole {@code Mailbox} entity to read one field off it. Everything else a caller might want from a
 * mailbox belongs to whoever owns mailboxes.
 */
public interface MailboxDirectory {

    /**
     * The address of a live mailbox in this organization, or empty when there is none.
     *
     * <p>Empty covers deleted and never-existed alike, and both mean the same thing to a caller: do
     * not send. The organization is a parameter rather than taken from the tenant context because
     * the callers run on background threads where that context is set explicitly.
     */
    Optional<String> addressOf(UUID organizationId, UUID mailboxId);
}
