package com.prabhix.platform.commerce.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_order_counters")
@IdClass(CommerceOrderCounter.CommerceOrderCounterId.class)
public class CommerceOrderCounter {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Id
    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    public record CommerceOrderCounterId(UUID organizationId, String financialYear) implements Serializable {
    }
}
