package com.prabhix.platform.billing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads organization billing fields without importing the org module. */
@Component
@RequiredArgsConstructor
public class BillingOrgReader {

    private final JdbcTemplate jdbc;

    @Value("${prabhix.billing.invoice.place-of-supply:Karnataka}")
    private String placeOfSupply;

    public Optional<BillingOrgSnapshot> find(UUID organizationId) {
        List<BillingOrgSnapshot> rows = jdbc.query("""
                        SELECT name, legal_name, gstin, billing_email, billing_address
                        FROM organizations WHERE id = ? AND deleted_at IS NULL
                        """,
                (rs, rowNum) -> new BillingOrgSnapshot(
                        rs.getString("name"),
                        rs.getString("legal_name"),
                        rs.getString("gstin"),
                        rs.getString("billing_email"),
                        readJsonMap(rs.getString("billing_address"))),
                organizationId);
        return rows.stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public void suspendOrganization(UUID organizationId) {
        jdbc.update("UPDATE organizations SET status = 'SUSPENDED', updated_at = now() WHERE id = ?",
                organizationId);
    }

    public void activateOrganization(UUID organizationId) {
        jdbc.update("UPDATE organizations SET status = 'ACTIVE', updated_at = now() WHERE id = ?",
                organizationId);
    }

    public void updateSeatLimit(UUID organizationId, int seatLimit) {
        jdbc.update("UPDATE organizations SET seat_limit = ?, updated_at = now() WHERE id = ?",
                seatLimit, organizationId);
    }

    public String placeOfSupply() {
        return placeOfSupply;
    }

    public record BillingOrgSnapshot(
            String name,
            String legalName,
            String gstin,
            String billingEmail,
            Map<String, Object> billingAddress) {

        public String displayName() {
            return legalName != null && !legalName.isBlank() ? legalName : name;
        }

        public String buyerState() {
            if (billingAddress == null) {
                return null;
            }
            Object state = billingAddress.get("state");
            return state == null ? null : state.toString();
        }
    }
}
