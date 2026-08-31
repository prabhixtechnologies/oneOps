package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.common.util.Json;
import com.prabhix.platform.mail.util.MailSubjectUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves which thread an inbound message belongs to using the 4-step algorithm from MAIL.md.
 */
@Component
@RequiredArgsConstructor
public class ThreadResolver {

    private final MailMessageRepository messageRepository;
    private final MailThreadRepository threadRepository;
    private final PrabhixProperties properties;

    public MailThread resolve(UUID organizationId, UUID mailboxId, MimeParser.ParsedMime parsed) {
        List<String> refIds = MimeParser.parseReferenceIds(parsed.getInReplyTo(), parsed.getReferencesHeader());
        if (!refIds.isEmpty()) {
            List<MailMessage> matches = messageRepository.findByMessageIdHeaders(refIds);
            Optional<MailMessage> inMailbox = matches.stream()
                    .filter(m -> m.getMailboxId().equals(mailboxId))
                    .findFirst();
            if (inMailbox.isPresent()) {
                return threadRepository.findById(inMailbox.get().getThreadId()).orElseThrow();
            }
        }

        String prefix = properties.mail().threading().tokenPrefix();
        String refKey = MailSubjectUtil.extractReferenceKey(parsed.getSubject(), prefix);
        if (refKey != null) {
            Optional<MailThread> byToken = threadRepository.findByReferenceKey(refKey);
            if (byToken.isPresent() && byToken.get().getMailboxId().equals(mailboxId)) {
                return byToken.get();
            }
        }

        String normalized = MailSubjectUtil.normalize(parsed.getSubject());
        Instant since = Instant.now().minus(properties.mail().threading().subjectFallbackWindow());
        Set<String> participants = buildParticipantSet(parsed);
        List<MailThread> candidates = threadRepository.findSubjectFallbackCandidates(mailboxId, normalized, since);
        for (MailThread candidate : candidates) {
            Set<String> threadParticipants = new HashSet<>(Json.parseStringList(candidate.getParticipantEmails()));
            if (!threadParticipants.isEmpty() && !intersect(participants, threadParticipants).isEmpty()) {
                return candidate;
            }
        }

        return createThread(organizationId, mailboxId, parsed, normalized, prefix);
    }

    private MailThread createThread(UUID organizationId, UUID mailboxId,
                                    MimeParser.ParsedMime parsed, String normalized, String prefix) {
        MailThread thread = new MailThread();
        thread.setOrganizationId(organizationId);
        thread.setMailboxId(mailboxId);
        String refKey = Ids.readableCode(6);
        thread.setReferenceKey(refKey);
        thread.setSubject(parsed.getSubject() != null ? parsed.getSubject() : "(no subject)");
        thread.setNormalizedSubject(normalized);
        thread.setCustomerEmail(parsed.getFrom());
        thread.setCustomerName(parsed.getFromName());
        thread.setParticipantEmails(Json.toJson(buildParticipantSet(parsed)));
        thread.setLastMessageAt(Instant.now());
        thread.setLastMessageDirection(MailEnums.MessageDirection.INBOUND);
        return threadRepository.save(thread);
    }

    private Set<String> buildParticipantSet(MimeParser.ParsedMime parsed) {
        Set<String> set = new HashSet<>();
        if (parsed.getFrom() != null) {
            set.add(parsed.getFrom().toLowerCase());
        }
        parsed.getToAddresses().forEach(a -> set.add(a.toLowerCase()));
        parsed.getCcAddresses().forEach(a -> set.add(a.toLowerCase()));
        return set;
    }

    private Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}
