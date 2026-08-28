package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceOrderCounter;
import com.prabhix.platform.commerce.repository.CommerceOrderCounterRepository;
import com.prabhix.platform.commerce.repository.CommerceSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNumberContentionTest {

    @Mock private CommerceOrderCounterRepository counterRepository;
    @Mock private CommerceSettingsRepository settingsRepository;

    @Test
    void nextSequenceIncrementsGaplessly() {
        UUID orgId = UUID.randomUUID();
        AtomicInteger seq = new AtomicInteger(0);
        when(counterRepository.lockByOrgAndFinancialYear(orgId, "2026-27")).thenAnswer(inv -> {
            CommerceOrderCounter counter = new CommerceOrderCounter();
            counter.setOrganizationId(orgId);
            counter.setFinancialYear("2026-27");
            counter.setLastSequence(seq.get());
            return Optional.of(counter);
        });
        when(counterRepository.save(any())).thenAnswer(inv -> {
            CommerceOrderCounter c = inv.getArgument(0);
            seq.set(c.getLastSequence());
            return c;
        });

        OrderNumberService service = new OrderNumberService(counterRepository, settingsRepository);

        assertEquals(1, service.nextSequence(orgId, "2026-27"));
        assertEquals(2, service.nextSequence(orgId, "2026-27"));
        assertEquals(3, service.nextSequence(orgId, "2026-27"));
    }
}
