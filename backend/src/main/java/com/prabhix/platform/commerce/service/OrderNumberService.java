package com.prabhix.platform.commerce.service;

import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.commerce.domain.CommerceOrderCounter;
import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.repository.CommerceOrderCounterRepository;
import com.prabhix.platform.commerce.repository.CommerceSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderNumberService {

    private final CommerceOrderCounterRepository counterRepository;
    private final CommerceSettingsRepository settingsRepository;

    @Transactional
    public String nextOrderNumber(UUID organizationId) {
        CommerceSettings settings = settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> defaultSettings(organizationId));
        LocalDate today = LocalDate.now();
        String fy = BillingAmountCalculator.financialYear(today);
        int sequence = nextSequence(organizationId, fy);
        return BillingAmountCalculator.formatInvoiceNumber(settings.getOrderNumberPrefix(), fy, sequence);
    }

    @Transactional
    public int nextSequence(UUID organizationId, String financialYear) {
        CommerceOrderCounter counter = counterRepository.lockByOrgAndFinancialYear(organizationId, financialYear)
                .orElseGet(() -> {
                    CommerceOrderCounter created = new CommerceOrderCounter();
                    created.setOrganizationId(organizationId);
                    created.setFinancialYear(financialYear);
                    created.setLastSequence(0);
                    created.setUpdatedAt(Instant.now());
                    return counterRepository.save(created);
                });
        int next = counter.getLastSequence() + 1;
        counter.setLastSequence(next);
        counter.setUpdatedAt(Instant.now());
        counterRepository.save(counter);
        return next;
    }

    private CommerceSettings defaultSettings(UUID organizationId) {
        CommerceSettings settings = new CommerceSettings();
        settings.setOrganizationId(organizationId);
        return settings;
    }
}
