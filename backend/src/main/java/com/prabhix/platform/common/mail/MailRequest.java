package com.prabhix.platform.common.mail;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Please send this email." Constructed by any module; understood only by {@code mail}.
 *
 * <p>This is why {@code billing}, {@code auth}, {@code chat}, {@code commerce} and {@code site} have
 * no compile-time dependency on the mail module. They state the intent; mail decides how it is
 * rendered, queued, retried, and which transport carries it.
 *
 * <p>Was a Spring event, {@code MailRequested}, delivered to a listener AFTER_COMMIT. Now handed to
 * {@link MailClient}, which writes it down inside the caller's transaction. The difference is what
 * happens when the process dies at the wrong moment: the event left no record that mail had been
 * asked for, so there was nothing to retry and nothing to find.
 *
 * @param organizationId tenant this send belongs to, or {@code null} for platform mail such as a
 *                       magic link to someone who has not joined an organization yet
 * @param templateKey    key in {@code mail_templates}, e.g. {@code auth.magic-link}
 * @param locale         template locale; falls back to {@code en}
 * @param to             recipient addresses, at least one
 * @param variables      values for the template's declared variables
 * @param dedupeKey      idempotency handle. When set, asking twice is one email, which makes
 *                       retries and redeploys safe
 * @param priority       0 is highest (a user is waiting), 100 is bulk. Defaults to 50
 */
public record MailRequest(
        UUID organizationId,
        String templateKey,
        String locale,
        List<String> to,
        Map<String, Object> variables,
        String dedupeKey,
        int priority) {

    /** Auth and other "user is waiting on this" mail. */
    public static final int PRIORITY_INTERACTIVE = 0;
    public static final int PRIORITY_NORMAL = 50;
    public static final int PRIORITY_BULK = 90;

    public MailRequest {
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

    public static MailRequest to(String address,
                                 String templateKey,
                                 Map<String, Object> variables) {
        return new MailRequest(null, templateKey, "en", List.of(address), variables,
                null, PRIORITY_NORMAL);
    }

    public static MailRequest interactive(String address,
                                          String templateKey,
                                          Map<String, Object> variables,
                                          String dedupeKey) {
        return new MailRequest(null, templateKey, "en", List.of(address), variables,
                dedupeKey, PRIORITY_INTERACTIVE);
    }

    public static MailRequest forOrganization(UUID organizationId,
                                              String address,
                                              String templateKey,
                                              Map<String, Object> variables,
                                              String dedupeKey) {
        return new MailRequest(organizationId, templateKey, "en", List.of(address), variables,
                dedupeKey, PRIORITY_NORMAL);
    }
}
