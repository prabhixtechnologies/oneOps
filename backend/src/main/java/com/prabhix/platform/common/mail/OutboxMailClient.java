package com.prabhix.platform.common.mail;

import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes the request down and returns. The only implementation of {@link MailClient}.
 *
 * <p>{@code Propagation.MANDATORY} is the important line. Every caller is asking for mail about
 * something it is in the middle of doing, and the guarantee worth having is that the request and
 * that work commit together. Joining an existing transaction gives it; starting one of its own
 * quietly does not, and the caller would have no way to tell the difference until the day a rollback
 * left an email queued for an invitation that was never created.
 */
@Service
@RequiredArgsConstructor
public class OutboxMailClient implements MailClient {

    private static final int MAX_ATTEMPTS = 6;

    private final MailRequestRepository requests;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID send(MailRequest request) {
        String recipients = Json.toJson(request.to());
        String variables = Json.toJson(request.variables());

        if (request.dedupeKey() == null || request.dedupeKey().isBlank()) {
            MailRequestRecord row = new MailRequestRecord();
            row.setOrganizationId(request.organizationId());
            row.setTemplateKey(request.templateKey());
            row.setLocale(request.locale());
            row.setToAddresses(recipients);
            row.setVariables(variables);
            row.setPriority(request.priority());
            row.setMaxAttempts(MAX_ATTEMPTS);
            return requests.save(row).getId();
        }

        UUID inserted = requests.insertWithDedupe(
                request.organizationId(), request.templateKey(), request.locale(),
                recipients, variables, request.dedupeKey(), request.priority(), MAX_ATTEMPTS);
        if (inserted != null) {
            return inserted;
        }

        // Lost the insert, so the row is someone else's. Returning its id rather than throwing is
        // what makes a dedupe key mean "this email happens once" instead of "whoever asks second
        // gets an error" — the caller asked for one email and there is one.
        return requests.findByDedupeKey(request.dedupeKey())
                .map(MailRequestRecord::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Dedupe key " + request.dedupeKey() + " conflicted but no row exists"));
    }
}
