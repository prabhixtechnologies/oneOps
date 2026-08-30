package com.prabhix.platform.billing.service;

import tools.jackson.databind.ObjectMapper;
import com.prabhix.platform.billing.dto.BillingDtos.BillingAddressView;
import com.prabhix.platform.billing.dto.BillingDtos.UpdateBillingAddressRequest;
import com.prabhix.platform.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillingAddressService {

    private final BillingOrgReader orgReader;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BillingAddressView getAddress(UUID organizationId) {
        var org = orgReader.find(organizationId)
                .orElseThrow(() -> ApiException.notFound("Organization"));
        Map<String, Object> address = org.billingAddress() == null ? Map.of() : org.billingAddress();
        return toView(address, org.gstin(), org.billingEmail());
    }

    @Transactional
    public BillingAddressView updateAddress(UUID organizationId, UpdateBillingAddressRequest request) {
        orgReader.find(organizationId).orElseThrow(() -> ApiException.notFound("Organization"));

        Map<String, Object> address = Map.of(
                "line1", request.line1(),
                "line2", request.line2() == null ? "" : request.line2(),
                "city", request.city(),
                "state", request.state(),
                "pincode", request.pincode(),
                "country", request.country() == null ? "IN" : request.country());

        try {
            String json = objectMapper.writeValueAsString(address);
            jdbc.update("""
                    UPDATE organizations
                    SET billing_address = ?::jsonb,
                        gstin = COALESCE(?, gstin),
                        billing_email = COALESCE(?, billing_email),
                        updated_at = now()
                    WHERE id = ? AND deleted_at IS NULL
                    """,
                    json,
                    blankToNull(request.gstin()),
                    blankToNull(request.billingEmail()),
                    organizationId);
        } catch (Exception ex) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.INTERNAL_ERROR,
                    "Could not save billing address", ex);
        }
        return getAddress(organizationId);
    }

    private BillingAddressView toView(Map<String, Object> address, String gstin, String billingEmail) {
        return new BillingAddressView(
                stringVal(address, "line1"),
                stringVal(address, "line2"),
                stringVal(address, "city"),
                stringVal(address, "state"),
                stringVal(address, "pincode"),
                stringVal(address, "country"),
                gstin,
                billingEmail);
    }

    private String stringVal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
