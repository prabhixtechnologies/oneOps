package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.repository.CommerceSettingsRepository;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommerceSettingsService {

    private final CommerceSettingsRepository settingsRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public CommerceDtos.SettingsView get(UUID organizationId) {
        return toView(resolve(organizationId));
    }

    @Transactional
    public CommerceDtos.SettingsView update(PrabhixPrincipal principal, CommerceDtos.UpdateSettingsRequest request) {
        UUID orgId = principal.requireOrganizationId();
        CommerceSettings settings = resolve(orgId);
        if (request.sellerState() != null) {
            settings.setSellerState(request.sellerState());
        }
        if (request.sellerName() != null) {
            settings.setSellerName(request.sellerName());
        }
        if (request.sellerGstin() != null) {
            settings.setSellerGstin(request.sellerGstin());
        }
        if (request.sellerAddress() != null) {
            settings.setSellerAddress(request.sellerAddress());
        }
        if (request.orderNumberPrefix() != null) {
            settings.setOrderNumberPrefix(request.orderNumberPrefix());
        }
        if (request.gstPercent() != null) {
            settings.setGstPercent(request.gstPercent());
        }
        if (request.flatShippingMinor() != null) {
            settings.setFlatShippingMinor(request.flatShippingMinor());
        }
        if (request.freeShippingAboveMinor() != null) {
            settings.setFreeShippingAboveMinor(request.freeShippingAboveMinor());
        }
        settings = settingsRepository.save(settings);
        events.publishEvent(AuditRequested.of(orgId, principal.userId(),
                "commerce.settings.updated", "commerce_settings", settings.getId()));
        return toView(settings);
    }

    @Transactional
    public CommerceSettings resolve(UUID organizationId) {
        return settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> {
                    CommerceSettings created = new CommerceSettings();
                    created.setOrganizationId(organizationId);
                    return settingsRepository.save(created);
                });
    }

    private CommerceDtos.SettingsView toView(CommerceSettings settings) {
        return new CommerceDtos.SettingsView(
                settings.getSellerState(),
                settings.getSellerName(),
                settings.getSellerGstin(),
                settings.getSellerAddress(),
                settings.getOrderNumberPrefix(),
                settings.getGstPercent(),
                settings.getFlatShippingMinor(),
                settings.getFreeShippingAboveMinor());
    }
}
