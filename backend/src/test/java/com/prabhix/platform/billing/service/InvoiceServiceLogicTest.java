package com.prabhix.platform.billing.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceServiceLogicTest {

    @Test
    void financialYear_beforeAprilUsesPreviousStartYear() {
        assertEquals("2025-26", BillingAmountCalculator.financialYear(LocalDate.of(2026, 1, 15)));
    }

    @Test
    void financialYear_fromAprilUsesCurrentStartYear() {
        assertEquals("2026-27", BillingAmountCalculator.financialYear(LocalDate.of(2026, 8, 27)));
    }

    @Test
    void cgstSgstWhenBuyerStateMatchesSupplier() {
        var tax = BillingAmountCalculator.computeTax(10_000, 18, "Karnataka", "Karnataka");
        assertEquals(900, tax.cgstPaise());
        assertEquals(900, tax.sgstPaise());
        assertEquals(0, tax.igstPaise());
        assertEquals(1800, tax.totalTaxPaise());
    }

    @Test
    void igstWhenBuyerStateDiffers() {
        var tax = BillingAmountCalculator.computeTax(10_000, 18, "Maharashtra", "Karnataka");
        assertEquals(0, tax.cgstPaise());
        assertEquals(0, tax.sgstPaise());
        assertEquals(1800, tax.igstPaise());
    }

    @Test
    void gaplessInvoiceNumberFormat() {
        assertEquals("PBX/2026-27/000042",
                BillingAmountCalculator.formatInvoiceNumber("PBX", "2026-27", 42));
    }
}
