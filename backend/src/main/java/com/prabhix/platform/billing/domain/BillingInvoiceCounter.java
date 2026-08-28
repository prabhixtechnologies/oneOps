package com.prabhix.platform.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "billing_invoice_counters")
public class BillingInvoiceCounter {

    @Id
    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
