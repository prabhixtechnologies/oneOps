package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainVerificationRecheckTest {

    @Mock MailDomainRepository domainRepository;
    @Mock ApplicationEventPublisher events;

    @BeforeEach
    void resetMocks() {
        reset(domainRepository, events);
    }

    @Test
    void recheckSkipsWhenDisabled() {
        PrabhixProperties disabled = TestProperties.withMail(new PrabhixProperties.Mail(
                "a@b.com", "n", "r", "LOGGING",
                TestProperties.outbox(), TestProperties.inbound(), TestProperties.tracking(),
                TestProperties.threading(), TestProperties.ses("", ""),
                TestProperties.credentials(),
                new PrabhixProperties.Mail.DomainVerification(false, "0 0 * * * *",
                        Duration.ofDays(1), 50)));
        DomainVerificationService service = new DomainVerificationService(domainRepository, disabled, events);

        service.recheckVerifiedDomains();

        verifyNoInteractions(domainRepository);
    }

    @Test
    void recheckClaimsBatchWithSkipLocked() {
        DomainVerificationService service = new DomainVerificationService(domainRepository,
                TestProperties.defaults(), events);
        when(domainRepository.claimDueForRecheck(any(), anyInt())).thenReturn(List.of());

        service.recheckVerifiedDomains();

        verify(domainRepository).claimDueForRecheck(any(Instant.class), eq(50));
    }

    @Test
    void dnsRegressionFlipsVerifiedDomainToFailed() {
        MailDomain domain = verifiedDomain();
        when(domainRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(domain.getId(), domain.getOrganizationId()))
                .thenReturn(Optional.of(domain));
        when(domainRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DomainVerificationService service = new DomainVerificationService(domainRepository,
                TestProperties.defaults(), events) {
            @Override
            String lookup(String name, String type) {
                return null;
            }
        };

        service.verify(domain.getId(), domain.getOrganizationId());

        assertEquals(MailEnums.DomainStatus.FAILED, domain.getStatus());
        assertNull(domain.getMxVerifiedAt());
        ArgumentCaptor<AuditRequested> audit = ArgumentCaptor.forClass(AuditRequested.class);
        verify(events).publishEvent(audit.capture());
        assertEquals("mail.domain.regressed", audit.getValue().action());
    }

    private MailDomain verifiedDomain() {
        MailDomain domain = new MailDomain();
        domain.setId(UUID.randomUUID());
        domain.setOrganizationId(UUID.randomUUID());
        domain.setDomain("acme.com");
        domain.setStatus(MailEnums.DomainStatus.VERIFIED);
        domain.setVerificationToken("tok");
        domain.setDkimSelector("pbx1");
        domain.setDkimPublicKey("key");
        domain.setMxVerifiedAt(Instant.now());
        domain.setOwnershipVerifiedAt(Instant.now());
        return domain;
    }
}
