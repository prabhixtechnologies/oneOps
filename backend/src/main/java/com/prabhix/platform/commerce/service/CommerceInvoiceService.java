package com.prabhix.platform.commerce.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.commerce.domain.CommerceInvoice;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.domain.OrderAddress;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.domain.CommerceEnums.AddressType;
import com.prabhix.platform.commerce.domain.CommerceEnums.InvoiceStatus;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceInvoiceRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.OrderAddressRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.files.domain.StoredFile;
import com.prabhix.platform.files.service.FileStorageService;
import com.prabhix.platform.security.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommerceInvoiceService {

    private final CommerceInvoiceRepository invoiceRepository;
    private final CommerceOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderAddressRepository addressRepository;
    private final CommerceCustomerRepository customerRepository;
    private final CommerceSettingsService settingsService;
    private final OrderNumberService orderNumberService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadPdfByAccessToken(String accessToken) {
        CommerceOrder order = orderRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        if (order.getInvoiceId() == null) {
            throw ApiException.notFound("Invoice");
        }
        CommerceInvoice invoice = invoiceRepository.findByIdAndOrganizationId(
                        order.getInvoiceId(), order.getOrganizationId())
                .orElseThrow(() -> ApiException.notFound("Invoice"));
        if (invoice.getPdfFileId() == null) {
            throw ApiException.notFound("Invoice PDF");
        }
        byte[] pdf = TenantContext.callAs(order.getOrganizationId(), () ->
                fileStorageService.read(order.getOrganizationId(), invoice.getPdfFileId()));
        Resource body = new ByteArrayResource(pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(invoice.getInvoiceNumber() + ".pdf", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(body);
    }

    @Transactional
    public CommerceInvoice issueForOrder(CommerceOrder order) {
        var existing = invoiceRepository.findByOrderId(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        CommerceSettings settings = settingsService.resolve(order.getOrganizationId());
        LocalDate issueDate = LocalDate.now();
        String fy = BillingAmountCalculator.financialYear(issueDate);
        int sequence = orderNumberService.nextSequence(order.getOrganizationId(), fy);
        String invoiceNumber = BillingAmountCalculator.formatInvoiceNumber("INV", fy, sequence);

        List<OrderItem> items = orderItemRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        List<Map<String, Object>> lineItems = new ArrayList<>();
        for (OrderItem item : items) {
            lineItems.add(Map.of(
                    "description", item.getProductName() + " — " + item.getVariantName(),
                    "quantity", item.getQuantity(),
                    "unitMinor", item.getUnitPriceMinor(),
                    "amountMinor", item.getLineSubtotalMinor(),
                    "hsnCode", item.getHsnCode() == null ? "" : item.getHsnCode(),
                    "gstPercent", order.getGstPercent()));
        }

        OrderAddress billing = addressRepository.findByOrderIdAndOrganizationId(
                        order.getId(), order.getOrganizationId()).stream()
                .filter(a -> a.getAddressType() == AddressType.BILLING)
                .findFirst().orElse(null);

        var customer = order.getCustomerId() == null ? null
                : customerRepository.findById(order.getCustomerId()).orElse(null);

        CommerceInvoice invoice = new CommerceInvoice();
        invoice.setOrganizationId(order.getOrganizationId());
        invoice.setOrderId(order.getId());
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setFinancialYear(fy);
        invoice.setSequenceNumber(sequence);
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setIssueDate(issueDate);
        invoice.setBillToName(billing == null ? (customer == null ? "Customer" : customer.getName()) : billing.getName());
        invoice.setBillToEmail(customer == null ? null : customer.getEmail());
        invoice.setBillToAddress(billing == null ? Map.of() : Map.of(
                "line1", billing.getLine1(),
                "city", billing.getCity(),
                "state", billing.getState(),
                "pincode", billing.getPincode()));
        invoice.setLineItems(lineItems);
        invoice.setSubtotalMinor(order.getSubtotalMinor());
        invoice.setDiscountMinor(order.getDiscountMinor());
        invoice.setCgstMinor(order.getCgstMinor());
        invoice.setSgstMinor(order.getSgstMinor());
        invoice.setIgstMinor(order.getIgstMinor());
        invoice.setTotalMinor(order.getTotalMinor());
        invoice.setCurrency(order.getCurrency());
        invoice.setPlaceOfSupply(order.getBuyerState() == null ? settings.getSellerState() : order.getBuyerState());
        invoice.setPaidAt(Instant.now());

        try {
            invoice = invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            return invoiceRepository.findByOrderId(order.getId()).orElseThrow(() -> ex);
        }
        invoice.setPdfFileId(storePdf(invoice, settings).getId());
        try {
            return invoiceRepository.save(invoice);
        } catch (DataIntegrityViolationException ex) {
            return invoiceRepository.findByOrderId(order.getId()).orElseThrow(() -> ex);
        }
    }

    private StoredFile storePdf(CommerceInvoice invoice, CommerceSettings settings) {
        byte[] pdf = renderPdf(invoice, settings);
        return TenantContext.callAs(invoice.getOrganizationId(), () -> fileStorageService.store(
                pdf,
                invoice.getInvoiceNumber() + ".pdf",
                "application/pdf",
                StoredFile.FilePurpose.INVOICE,
                null));
    }

    byte[] renderPdf(CommerceInvoice invoice, CommerceSettings settings) {
        String xhtml = renderXhtml(invoice, settings);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception ex) {
            throw ApiException.of(ErrorCode.STORAGE_ERROR, "Could not render invoice PDF", ex);
        }
    }

    String renderXhtml(CommerceInvoice invoice, CommerceSettings settings) {
        boolean intraState = invoice.getIgstMinor() == 0;
        String taxRows = intraState
                ? """
                <tr><td colspan="4">CGST</td><td>%s</td></tr>
                <tr><td colspan="4">SGST</td><td>%s</td></tr>
                """.formatted(formatRupee(invoice.getCgstMinor()), formatRupee(invoice.getSgstMinor()))
                : """
                <tr><td colspan="4">IGST</td><td>%s</td></tr>
                """.formatted(formatRupee(invoice.getIgstMinor()));
        return """
                <!DOCTYPE html><html><head><meta charset="UTF-8"/></head><body>
                <h1>Tax Invoice %s</h1>
                <p>From: %s<br/>GSTIN: %s</p>
                <p>Bill to: %s</p>
                <table border="1" cellpadding="4" cellspacing="0" width="100%%">
                <tr><th>Description</th><th>Qty</th><th>Rate</th><th>Amount</th></tr>
                %s
                <tr><td colspan="4">Subtotal</td><td>%s</td></tr>
                <tr><td colspan="4">Discount</td><td>%s</td></tr>
                %s
                <tr><td colspan="4"><strong>Total</strong></td><td><strong>%s</strong></td></tr>
                </table></body></html>
                """.formatted(
                escape(invoice.getInvoiceNumber()),
                escape(settings.getSellerName() == null ? "Seller" : settings.getSellerName()),
                escape(settings.getSellerGstin() == null ? "—" : settings.getSellerGstin()),
                escape(invoice.getBillToName()),
                lineRows(invoice),
                formatRupee(invoice.getSubtotalMinor()),
                formatRupee(invoice.getDiscountMinor()),
                taxRows,
                formatRupee(invoice.getTotalMinor()));
    }

    private String lineRows(CommerceInvoice invoice) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> line : invoice.getLineItems()) {
            sb.append("<tr><td>").append(escape(String.valueOf(line.get("description"))))
                    .append("</td><td>").append(line.get("quantity"))
                    .append("</td><td>").append(formatRupee(((Number) line.get("unitMinor")).longValue()))
                    .append("</td><td>").append(formatRupee(((Number) line.get("amountMinor")).longValue()))
                    .append("</td></tr>");
        }
        return sb.toString();
    }

    private static String formatRupee(long minor) {
        return "₹" + String.format("%,.2f", minor / 100.0);
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
