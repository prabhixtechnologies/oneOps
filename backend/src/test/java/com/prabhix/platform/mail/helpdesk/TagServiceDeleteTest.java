package com.prabhix.platform.mail.helpdesk;

import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.mail.domain.MailTag;
import com.prabhix.platform.mail.repository.MailTagRepository;
import com.prabhix.platform.mail.repository.MailThreadRepository;
import com.prabhix.platform.mail.repository.MailThreadTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceDeleteTest {

    @Mock MailTagRepository tagRepository;
    @Mock MailThreadTagRepository threadTagRepository;
    @Mock MailThreadRepository threadRepository;
    @Mock ApplicationEventPublisher events;

    TagService service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepository, threadTagRepository, threadRepository, events);
    }

    @Test
    void deleteDetachesFromThreads() {
        MailTag tag = new MailTag();
        tag.setId(tagId);
        tag.setOrganizationId(orgId);
        tag.setName("VIP");
        when(tagRepository.findByIdAndOrganizationId(tagId, orgId)).thenReturn(Optional.of(tag));

        service.delete(orgId, tagId);

        verify(threadTagRepository).deleteByIdTagId(tagId);
        verify(tagRepository).delete(tag);
        verify(events).publishEvent(org.mockito.ArgumentMatchers.any(AuditRequested.class));
    }
}
