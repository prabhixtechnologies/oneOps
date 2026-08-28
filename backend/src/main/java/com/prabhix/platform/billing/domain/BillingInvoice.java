package com.prabhix.platform.billing.domain;

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
@Table(name = "billing_invoices")
public class BillingInvoice extends TenantScopedEntity {

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "invoice_number", nullable = false, length = 60, unique = true)
    private String invoiceNumber;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BillingEnums.InvoiceStatus status = BillingEnums.InvoiceStatus.ISSUED;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "bill_to_name", nullable = false, length = 250)
    private String billToName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bill_to_address", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> billToAddress = Map.of();

    @Column(name = "bill_to_gstin", length = 15)
    private String billToGstin;

    @Column(name = "bill_to_email", columnDefinition = "citext")
    private String billToEmail;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> lineItems = List.of();

    @Column(name = "subtotal_paise", nullable = false)
    private long subtotalPaise;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "cgst_paise", nullable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false)
    private long igstPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "sac_code", nullable = false, length = 12)
    private String sacCode = "998314";

    @Column(name = "place_of_supply", length = 80)
    private String placeOfSupply;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "pdf_file_id")
    private UUID pdfFileId;
}
