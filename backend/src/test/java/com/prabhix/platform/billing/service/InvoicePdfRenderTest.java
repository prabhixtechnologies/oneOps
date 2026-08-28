package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingEnums;
import com.prabhix.platform.billing.domain.BillingInvoice;
import com.prabhix.platform.billing.domain.BillingOrder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePdfRenderTest {

    @Test
    void xhtmlIncludesIntraStateTaxBreakdown() {
        InvoiceService service = new InvoiceService(null, null, null, null, null);

        BillingInvoice invoice = new BillingInvoice();
        invoice.setInvoiceNumber("PBX/2026-27/000001");
        invoice.setIssueDate(LocalDate.of(2026, 8, 27));
        invoice.setPlaceOfSupply("Karnataka");
        invoice.setSacCode("998314");
        invoice.setSubtotalPaise(10_000);
        invoice.setCgstPaise(900);
        invoice.setSgstPaise(900);
        invoice.setIgstPaise(0);
        invoice.setTotalPaise(11_800);
        invoice.setLineItems(List.of(Map.of("description", "Growth subscription")));
        invoice.setBillToAddress(Map.of("line1", "1 MG Road", "city", "Bengaluru", "state", "Karnataka"));

        String xhtml = service.renderXhtml(invoice, "Acme Pvt Ltd", 18);

        assertTrue(xhtml.contains("<?xml version=\"1.0\""));
        assertTrue(xhtml.contains("CGST @ 9%"));
        assertTrue(xhtml.contains("SGST @ 9%"));
        assertTrue(xhtml.contains("Acme Pvt Ltd"));
        assertTrue(xhtml.contains("₹100.00"));
    }

    @Test
    void xhtmlIncludesInterStateIgst() {
        InvoiceService service = new InvoiceService(null, null, null, null, null);

        BillingInvoice invoice = new BillingInvoice();
        invoice.setInvoiceNumber("PBX/2026-27/000002");
        invoice.setIssueDate(LocalDate.of(2026, 8, 27));
        invoice.setPlaceOfSupply("Karnataka");
        invoice.setSacCode("998314");
        invoice.setSubtotalPaise(10_000);
        invoice.setCgstPaise(0);
        invoice.setSgstPaise(0);
        invoice.setIgstPaise(1800);
        invoice.setTotalPaise(11_800);
        invoice.setLineItems(List.of(Map.of("description", "Growth subscription")));
        invoice.setBillToAddress(Map.of("state", "Maharashtra"));

        String xhtml = service.renderXhtml(invoice, "Acme", 18);

        assertTrue(xhtml.contains("IGST @ 18%"));
        assertTrue(xhtml.contains("₹118.00"));
    }
}
