package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingInvoiceCounter;
import com.prabhix.platform.billing.repository.BillingInvoiceCounterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceCounterContentionTest {

    @Mock
    private BillingInvoiceCounterRepository counterRepository;

    @Test
    void nextSequenceIncrementsGaplessly() {
        AtomicInteger seq = new AtomicInteger(0);
        when(counterRepository.lockByFinancialYear("2026-27")).thenAnswer(inv -> {
            BillingInvoiceCounter counter = new BillingInvoiceCounter();
            counter.setFinancialYear("2026-27");
            counter.setLastSequence(seq.get());
            return Optional.of(counter);
        });
        when(counterRepository.save(any())).thenAnswer(inv -> {
            BillingInvoiceCounter c = inv.getArgument(0);
            seq.set(c.getLastSequence());
            return c;
        });

        InvoiceService service = new InvoiceService(null, counterRepository, null, null, null);

        assertEquals(1, service.nextSequence("2026-27"));
        assertEquals(2, service.nextSequence("2026-27"));
        assertEquals(3, service.nextSequence("2026-27"));
    }
}
