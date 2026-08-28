package com.prabhix.platform.billing.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingInvoice;
import com.prabhix.platform.billing.domain.BillingInvoiceCounter;
import com.prabhix.platform.billing.domain.BillingOrder;
import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.billing.domain.BillingSubscription;
import com.prabhix.platform.billing.dto.BillingDtos.InvoiceDownload;
import com.prabhix.platform.billing.dto.BillingDtos.InvoiceSummary;
import com.prabhix.platform.billing.repository.BillingInvoiceCounterRepository;
import com.prabhix.platform.billing.repository.BillingInvoiceRepository;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.web.Cursor;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.config.PrabhixProperties.Billing.Invoice;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final BillingInvoiceRepository invoiceRepository;
    private final BillingInvoiceCounterRepository counterRepository;
    private final BillingOrgReader orgReader;
    private final FileStorageService fileStorageService;
    private final PrabhixProperties properties;

    @Value("${prabhix.billing.invoice.place-of-supply:Karnataka}")
    private String placeOfSupply;

    @Value("${prabhix.billing.invoice.supplier-name:Prabhix Technologies Pvt Ltd}")
    private String supplierName;

    @Value("${prabhix.billing.invoice.supplier-gstin:}")
    private String supplierGstin;

    @Value("${prabhix.billing.invoice.supplier-address:}")
    private String supplierAddress;

    @Transactional
    public BillingInvoice issueForOrder(BillingOrder order,
                                        BillingPlan plan,
                                        BillingSubscription subscription) {
        var existing = invoiceRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        var org = orgReader.find(order.getOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Organization"));

        LocalDate issueDate = LocalDate.now();
        String fy = BillingAmountCalculator.financialYear(issueDate);
        int sequence = nextSequence(fy);
        Invoice invoiceConfig = properties.billing().invoice();
        String invoiceNumber = BillingAmountCalculator.formatInvoiceNumber(
                invoiceConfig.prefix(), fy, sequence);

        long subtotal = order.getAmountPaise();
        var tax = BillingAmountCalculator.computeTax(
                subtotal, invoiceConfig.gstPercent(), org.buyerState(), placeOfSupply);

        String lineDescription = lineDescription(order, plan);
        BillingInvoice invoice = new BillingInvoice();
        invoice.setOrganizationId(order.getOrganizationId());
        invoice.setOrderId(order.getId());
        invoice.setSubscriptionId(subscription == null ? order.getSubscriptionId() : subscription.getId());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setFinancialYear(fy);
        invoice.setSequenceNumber(sequence);
        invoice.setStatus(BillingEnums.InvoiceStatus.ISSUED);
        invoice.setIssueDate(issueDate);
        invoice.setBillToName(org.displayName());
        invoice.setBillToAddress(org.billingAddress() == null ? Map.of() : org.billingAddress());
        invoice.setBillToGstin(org.gstin());
        invoice.setBillToEmail(org.billingEmail());
        invoice.setLineItems(List.of(Map.of(
                "description", lineDescription,
                "quantity", 1,
                "unitPaise", subtotal,
                "amountPaise", subtotal,
                "sacCode", "998314",
                "gstPercent", invoiceConfig.gstPercent())));
        invoice.setSubtotalPaise(subtotal);
        invoice.setDiscountPaise(0);
        invoice.setCgstPaise(tax.cgstPaise());
        invoice.setSgstPaise(tax.sgstPaise());
        invoice.setIgstPaise(tax.igstPaise());
        invoice.setTotalPaise(subtotal + tax.totalTaxPaise());
        invoice.setCurrency(order.getCurrency());
        invoice.setPlaceOfSupply(placeOfSupply);

        try {
            invoice = invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            return invoiceRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> ex);
        }
        invoice.setPdfFileId(storePdfInvoice(invoice, org.displayName(), invoiceConfig.gstPercent()).getId());
        try {
            return invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            return invoiceRepository.findByOrderId(order.getId())
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public BillingInvoice markPaid(BillingInvoice invoice) {
        invoice.setStatus(BillingEnums.InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public CursorPage<InvoiceSummary> listInvoices(UUID organizationId, String cursor, int limit) {
        Cursor decoded = Cursor.decode(cursor);
        Instant cursorCreated = decoded == null ? null : decoded.timestamp();
        UUID cursorId = decoded == null ? null : decoded.id();

        var rows = invoiceRepository.findPage(
                organizationId, cursorCreated, cursorId, PageRequest.of(0, limit + 1));
        return CursorPage.of(rows, limit, this::toSummary,
                inv -> Cursor.of(inv.getCreatedAt(), inv.getId()).encode());
    }

    @Transactional(readOnly = true)
    public InvoiceDownload download(UUID organizationId, UUID invoiceId) {
        BillingInvoice invoice = invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        if (invoice.getPdfFileId() == null) {
            throw ApiException.invalidState("That invoice has no downloadable document yet");
        }
        byte[] content = fileStorageService.read(organizationId, invoice.getPdfFileId());
        return new InvoiceDownload(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                "application/pdf",
                content);
    }

    int nextSequence(String financialYear) {
        BillingInvoiceCounter counter = counterRepository.lockByFinancialYear(financialYear)
                .orElseGet(() -> {
                    BillingInvoiceCounter created = new BillingInvoiceCounter();
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

    private StoredFile storePdfInvoice(BillingInvoice invoice, String buyerName, int gstPercent) {
        byte[] pdf = renderPdf(invoice, buyerName, gstPercent);
        return TenantContext.callAs(invoice.getOrganizationId(), () -> fileStorageService.store(
                pdf,
                invoice.getInvoiceNumber() + ".pdf",
                "application/pdf",
                StoredFile.FilePurpose.INVOICE,
                null));
    }

    byte[] renderPdf(BillingInvoice invoice, String buyerName, int gstPercent) {
        String xhtml = renderXhtml(invoice, buyerName, gstPercent);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.STORAGE_ERROR,
                    "Could not render invoice PDF", ex);
        }
    }

    String renderXhtml(BillingInvoice invoice, String buyerName, int gstPercent) {
        boolean intraState = invoice.getIgstPaise() == 0;
        String buyerAddress = formatAddress(invoice.getBillToAddress());
        String supplierAddr = supplierAddress == null || supplierAddress.isBlank()
                ? "Karnataka, India" : escape(supplierAddress);
        String supplierGst = supplierGstin == null || supplierGstin.isBlank()
                ? "—" : escape(supplierGstin);

        StringBuilder rows = new StringBuilder();
        for (Map<String, Object> item : invoice.getLineItems()) {
            rows.append("""
                    <tr>
                      <td>%s</td>
                      <td class="num">%s</td>
                      <td class="num">%s</td>
                      <td class="num">%s</td>
                      <td class="num">%d%%</td>
                      <td class="num">%s</td>
                    </tr>
                    """.formatted(
                    escape(String.valueOf(item.get("description"))),
                    escape(String.valueOf(item.getOrDefault("sacCode", invoice.getSacCode()))),
                    "1",
                    formatRupee(invoice.getSubtotalPaise()),
                    gstPercent,
                    formatRupee(invoice.getSubtotalPaise())));
        }

        String taxRows;
        if (intraState) {
            taxRows = """
                    <tr><td colspan="5">CGST @ %d%%</td><td class="num">%s</td></tr>
                    <tr><td colspan="5">SGST @ %d%%</td><td class="num">%s</td></tr>
                    """.formatted(
                    gstPercent / 2, formatRupee(invoice.getCgstPaise()),
                    gstPercent / 2, formatRupee(invoice.getSgstPaise()));
        } else {
            taxRows = """
                    <tr><td colspan="5">IGST @ %d%%</td><td class="num">%s</td></tr>
                    """.formatted(gstPercent, formatRupee(invoice.getIgstPaise()));
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN"
                  "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                  <title>%s</title>
                  <style type="text/css">
                    body { font-family: sans-serif; font-size: 11pt; color: #111; }
                    h1 { font-size: 18pt; margin-bottom: 4px; }
                    .meta { margin-bottom: 16px; }
                    .cols { width: 100%%; }
                    .cols td { vertical-align: top; width: 50%%; }
                    table.items { width: 100%%; border-collapse: collapse; margin-top: 12px; }
                    table.items th, table.items td { border: 1px solid #ccc; padding: 6px; }
                    table.items th { background: #f4f4f4; text-align: left; }
                    .num { text-align: right; }
                    .total { font-weight: bold; font-size: 12pt; }
                  </style>
                </head>
                <body>
                  <h1>Tax Invoice</h1>
                  <div class="meta">
                    <div><strong>Invoice No:</strong> %s</div>
                    <div><strong>Invoice Date:</strong> %s</div>
                    <div><strong>Place of Supply:</strong> %s</div>
                  </div>
                  <table class="cols"><tr>
                    <td>
                      <strong>Supplier</strong><br/>
                      %s<br/>
                      GSTIN: %s<br/>
                      %s
                    </td>
                    <td>
                      <strong>Bill To</strong><br/>
                      %s<br/>
                      %s<br/>
                      GSTIN: %s
                    </td>
                  </tr></table>
                  <table class="items">
                    <thead>
                      <tr>
                        <th>Description</th>
                        <th>SAC</th>
                        <th>Qty</th>
                        <th>Taxable Value</th>
                        <th>GST</th>
                        <th>Amount</th>
                      </tr>
                    </thead>
                    <tbody>
                      %s
                      <tr><td colspan="5">Taxable Value</td><td class="num">%s</td></tr>
                      %s
                      <tr class="total"><td colspan="5">Total (INR)</td><td class="num">%s</td></tr>
                    </tbody>
                  </table>
                </body>
                </html>
                """.formatted(
                escape(invoice.getInvoiceNumber()),
                escape(invoice.getInvoiceNumber()),
                invoice.getIssueDate(),
                escape(invoice.getPlaceOfSupply()),
                escape(supplierName),
                supplierGst,
                supplierAddr,
                escape(buyerName),
                buyerAddress,
                invoice.getBillToGstin() == null ? "—" : escape(invoice.getBillToGstin()),
                rows,
                formatRupee(invoice.getSubtotalPaise()),
                taxRows,
                formatRupee(invoice.getTotalPaise()));
    }

    private String lineDescription(BillingOrder order, BillingPlan plan) {
        return switch (order.getPurpose()) {
            case UPGRADE -> plan.getName() + " plan upgrade (prorated)";
            case SEAT_ADDITION -> plan.getName() + " additional seats (prorated)";
            case SUBSCRIPTION_RENEWAL -> plan.getName() + " subscription renewal";
            default -> plan.getName() + " subscription";
        };
    }

    private String formatAddress(Map<String, Object> address) {
        if (address == null || address.isEmpty()) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        addPart(parts, address, "line1");
        addPart(parts, address, "line2");
        addPart(parts, address, "city");
        addPart(parts, address, "state");
        addPart(parts, address, "pincode");
        return escape(String.join(", ", parts));
    }

    private void addPart(List<String> parts, Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value != null && !value.toString().isBlank()) {
            parts.add(value.toString());
        }
    }

    private String formatRupee(long paise) {
        return "₹" + String.format("%,.2f", paise / 100.0);
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private InvoiceSummary toSummary(BillingInvoice invoice) {
        return new InvoiceSummary(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus(),
                invoice.getIssueDate(),
                invoice.getTotalPaise(),
                invoice.getCurrency(),
                invoice.getPaidAt());
    }
}
