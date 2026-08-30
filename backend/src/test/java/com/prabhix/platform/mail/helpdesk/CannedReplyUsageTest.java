package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.mail.domain.MailCannedReply;
import com.prabhix.platform.mail.repository.MailCannedReplyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CannedReplyUsageTest {

    @Mock private MailCannedReplyRepository repository;
    @Mock private ApplicationEventPublisher events;

    @InjectMocks private CannedReplyService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID replyId = UUID.randomUUID();

    @Test
    void useIncrementsTheCounter() {
        MailCannedReply reply = new MailCannedReply();
        reply.setId(replyId);
        reply.setUsageCount(7);
        when(repository.findByIdAndOrganizationIdAndDeletedAtIsNull(replyId, orgId))
                .thenReturn(Optional.of(reply));

        service.recordUse(orgId, replyId);

        assertEquals(8, reply.getUsageCount());
        verify(repository).save(reply);
    }

    /**
     * A reply that names no canned reply must not reach the database at all — the reply path calls
     * this unconditionally, so the null case is the common one.
     */
    @Test
    void aNullIdIsNotALookup() {
        service.recordUse(orgId, null);

        verifyNoInteractions(repository);
    }

    /**
     * Best effort: a canned reply deleted between the agent picking it and the send completing must
     * not fail a customer-facing reply for the sake of a statistic.
     */
    @Test
    void anUnknownIdIsIgnored() {
        when(repository.findByIdAndOrganizationIdAndDeletedAtIsNull(replyId, orgId))
                .thenReturn(Optional.empty());

        service.recordUse(orgId, replyId);

        verify(repository, never()).save(any());
    }
}
