package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.mail.domain.MailMessage;
import com.prabhix.platform.mail.domain.MailThread;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailMessageRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadResolverTest {

    @Mock
    MailMessageRepository messageRepository;
    @Mock
    MailThreadRepository threadRepository;

    ThreadResolver resolver;

    @BeforeEach
    void setUp() {
        PrabhixProperties props = TestProperties.defaults();
        resolver = new ThreadResolver(messageRepository, threadRepository, props);
    }

    @Test
    void resolvesByInReplyTo() {
        UUID orgId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();

        MimeParser.ParsedMime parsed = new MimeParser.ParsedMime();
        parsed.setInReplyTo("<msg-1>");
        parsed.setSubject("Re: hello");

        MailMessage existing = new MailMessage();
        existing.setThreadId(threadId);
        existing.setMailboxId(mailboxId);
        when(messageRepository.findByMessageIdHeaders(anyList())).thenReturn(List.of(existing));

        MailThread thread = new MailThread();
        thread.setId(threadId);
        when(threadRepository.findById(threadId)).thenReturn(Optional.of(thread));

        assertEquals(thread, resolver.resolve(orgId, mailboxId, parsed));
        verify(threadRepository, never()).save(any());
    }

    @Test
    void resolvesByThreadToken() {
        UUID orgId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();

        MimeParser.ParsedMime parsed = new MimeParser.ParsedMime();
        parsed.setSubject("Re: Issue [#PBX-ABC123]");

        MailThread thread = new MailThread();
        thread.setReferenceKey("ABC123");
        thread.setMailboxId(mailboxId);
        when(threadRepository.findByReferenceKey("ABC123")).thenReturn(Optional.of(thread));

        assertEquals(thread, resolver.resolve(orgId, mailboxId, parsed));
    }

    @Test
    void resolvesByNormalizedSubjectAndParticipants() {
        UUID orgId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();

        MimeParser.ParsedMime parsed = new MimeParser.ParsedMime();
        parsed.setSubject("Re: Billing question");
        parsed.setFrom("customer@acme.com");
        parsed.setToAddresses(List.of("support@example.com"));

        MailThread candidate = new MailThread();
        candidate.setId(UUID.randomUUID());
        candidate.setParticipantEmails("[\"customer@acme.com\",\"support@example.com\"]");
        when(threadRepository.findSubjectFallbackCandidates(eq(mailboxId), eq("billing question"), any()))
                .thenReturn(List.of(candidate));

        assertEquals(candidate, resolver.resolve(orgId, mailboxId, parsed));
    }

    @Test
    void createsNewThreadWhenNoMatch() {
        UUID orgId = UUID.randomUUID();
        UUID mailboxId = UUID.randomUUID();

        MimeParser.ParsedMime parsed = new MimeParser.ParsedMime();
        parsed.setSubject("Brand new");
        parsed.setFrom("new@acme.com");

        when(threadRepository.findSubjectFallbackCandidates(any(), any(), any())).thenReturn(List.of());
        when(threadRepository.save(any())).thenAnswer(inv -> {
            MailThread t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        MailThread result = resolver.resolve(orgId, mailboxId, parsed);
        assertNotNull(result.getReferenceKey());
        verify(threadRepository).save(any());
    }
}
