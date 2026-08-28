package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.mail.domain.MailCannedReply;
import com.prabhix.platform.mail.dto.CannedReplyDtos;
import com.prabhix.platform.mail.repository.MailCannedReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CannedReplyService {

    private final MailCannedReplyRepository repository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<CannedReplyDtos.CannedReplyResponse> list(UUID organizationId) {
        return repository.findByOrganizationIdAndDeletedAtIsNullOrderByTitle(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public CannedReplyDtos.CannedReplyResponse create(UUID organizationId,
                                                      CannedReplyDtos.CreateRequest request) {
        MailCannedReply reply = new MailCannedReply();
        reply.setOrganizationId(organizationId);
        reply.setMailboxId(request.mailboxId());
        reply.setShortcut(request.shortcut());
        reply.setTitle(request.title());
        reply.setSubject(request.subject());
        reply.setBodyHtml(request.bodyHtml());
        reply.setBodyText(request.bodyText());
        reply = repository.save(reply);
        events.publishEvent(AuditRequested.of(organizationId, null,
                "mail.canned_reply.created", "mail_canned_reply", reply.getId()));
        return toDto(reply);
    }

    @Transactional
    public CannedReplyDtos.CannedReplyResponse update(UUID organizationId, UUID replyId,
                                                      CannedReplyDtos.UpdateRequest request) {
        MailCannedReply reply = requireReply(organizationId, replyId);
        reply.setMailboxId(request.mailboxId());
        reply.setShortcut(request.shortcut());
        reply.setTitle(request.title());
        reply.setSubject(request.subject());
        reply.setBodyHtml(request.bodyHtml());
        reply.setBodyText(request.bodyText());
        reply = repository.save(reply);
        events.publishEvent(AuditRequested.changed(organizationId, null,
                "mail.canned_reply.updated", "mail_canned_reply", replyId,
                Map.of("title", request.title())));
        return toDto(reply);
    }

    @Transactional
    public void delete(UUID organizationId, UUID replyId) {
        MailCannedReply reply = requireReply(organizationId, replyId);
        reply.setDeletedAt(Instant.now());
        repository.save(reply);
        events.publishEvent(AuditRequested.of(organizationId, null,
                "mail.canned_reply.deleted", "mail_canned_reply", replyId));
    }

    private MailCannedReply requireReply(UUID organizationId, UUID replyId) {
        return repository.findByIdAndOrganizationIdAndDeletedAtIsNull(replyId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Canned reply"));
    }

    private CannedReplyDtos.CannedReplyResponse toDto(MailCannedReply r) {
        return new CannedReplyDtos.CannedReplyResponse(
                r.getId(), r.getMailboxId(), r.getShortcut(), r.getTitle(),
                r.getSubject(), r.getBodyHtml(), r.getUsageCount());
    }
}
