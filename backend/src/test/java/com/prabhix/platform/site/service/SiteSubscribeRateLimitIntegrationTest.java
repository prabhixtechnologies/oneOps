package com.prabhix.platform.site.service;

import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.site.dto.SiteDtos.GenericAck;
import com.prabhix.platform.site.dto.SiteDtos.SubscribeRequest;
import com.prabhix.platform.site.repository.SiteJobApplicationRepository;
import com.prabhix.platform.site.repository.SiteJobRoleRepository;
import com.prabhix.platform.site.repository.SiteLeadRepository;
import com.prabhix.platform.site.repository.SiteSubscriberRepository;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteSubscribeRateLimitIntegrationTest {

    @Mock private SiteLeadRepository leadRepository;
    @Mock private SiteSubscriberRepository subscriberRepository;
    @Mock private SiteJobRoleRepository jobRoleRepository;
    @Mock private SiteJobApplicationRepository applicationRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private ApplicationEventPublisher events;
    @Mock private MailClient mail;
    @Mock private SiteSubscribeRateLimiter subscribeRateLimiter;

    private SiteService siteService;

    @BeforeEach
    void setUp() {
        siteService = new SiteService(
                leadRepository,
                subscriberRepository,
                jobRoleRepository,
                applicationRepository,
                fileStorageService,
                events,
                mail,
                TestProperties.defaults(),
                subscribeRateLimiter);
    }

    @Test
    void returnsGenericAckWithoutSavingWhenRateLimited() {
        when(subscribeRateLimiter.isLimited("limited@example.com", "127.0.0.1")).thenReturn(true);

        GenericAck ack = siteService.subscribe(
                new SubscribeRequest("limited@example.com", "Limited", "footer"),
                "127.0.0.1");

        assertThat(ack.message()).isNotBlank();
        verify(subscriberRepository, never()).save(any());
    }
}
