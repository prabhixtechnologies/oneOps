package com.prabhix.platform.common.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Please send this email." Published by any module; consumed only by {@code mail}.
 *
 * <p>This event is why {@code billing}, {@code auth}, and {@code site} have no compile-time
 * dependency on the mail module. They state the intent; mail decides how it is rendered,
 * queued, retried, and which transport carries it.
 *
 * <p>Publish inside the business transaction. The listener is
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, so nothing is queued if the
 * transaction rolls back, and enqueuing can never fail the caller's work.
 *
 * @param organizationId tenant this send belongs to, or {@code null} for platform mail such
 *                       as a magic link to someone who has not joined an organization yet
 * @param templateKey    key in {@code mail_templates}, e.g. {@code auth.magic-link}
 * @param locale         template locale; falls back to {@code en}
 * @param to             recipient addresses, at least one
 * @param variables      values for the template's declared variables
 * @param dedupeKey      idempotency handle. When set, re-publishing the same key is a no-op,
 *                       which makes retries and redeploys safe
 * @param priority       0 is highest (a user is waiting), 100 is bulk. Defaults to 50
 */
public record MailRequested(
        UUID organizationId,
        String templateKey,
        String locale,
        List<String> to,
        Map<String, Object> variables,
        String dedupeKey,
        int priority) implements PlatformEvent {

    /** Auth and other "user is waiting on this" mail. */
    public static final int PRIORITY_INTERACTIVE = 0;
    public static final int PRIORITY_NORMAL = 50;
    public static final int PRIORITY_BULK = 90;

    public MailRequested {
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey is required");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("at least one recipient is required");
        }
        to = List.copyOf(to);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        locale = (locale == null || locale.isBlank()) ? "en" : locale;
    }

    public static MailRequested to(String address,
                                   String templateKey,
                                   Map<String, Object> variables) {
        return new MailRequested(null, templateKey, "en", List.of(address), variables,
                null, PRIORITY_NORMAL);
    }

    public static MailRequested interactive(String address,
                                            String templateKey,
                                            Map<String, Object> variables,
                                            String dedupeKey) {
        return new MailRequested(null, templateKey, "en", List.of(address), variables,
                dedupeKey, PRIORITY_INTERACTIVE);
    }

    public static MailRequested forOrganization(UUID organizationId,
                                                String address,
                                                String templateKey,
                                                Map<String, Object> variables,
                                                String dedupeKey) {
        return new MailRequested(organizationId, templateKey, "en", List.of(address), variables,
                dedupeKey, PRIORITY_NORMAL);
    }
}
