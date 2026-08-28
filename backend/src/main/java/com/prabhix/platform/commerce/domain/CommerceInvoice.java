package com.prabhix.platform.commerce.domain;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "commerce_invoices")
public class CommerceInvoice extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "invoice_number", nullable = false, length = 40)
    private String invoiceNumber;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CommerceEnums.InvoiceStatus status = CommerceEnums.InvoiceStatus.ISSUED;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "bill_to_name", nullable = false, length = 160)
    private String billToName;

    @Column(name = "bill_to_email", columnDefinition = "citext")
    private String billToEmail;

    @Column(name = "bill_to_gstin", length = 20)
    private String billToGstin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bill_to_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> billToAddress = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> lineItems = List.of();

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "cgst_minor", nullable = false)
    private long cgstMinor;

    @Column(name = "sgst_minor", nullable = false)
    private long sgstMinor;

    @Column(name = "igst_minor", nullable = false)
    private long igstMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "place_of_supply", nullable = false, length = 80)
    private String placeOfSupply;

    @Column(name = "pdf_file_id")
    private UUID pdfFileId;

    @Column(name = "paid_at")
    private Instant paidAt;
}
