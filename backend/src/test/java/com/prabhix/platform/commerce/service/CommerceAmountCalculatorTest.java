package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommerceAmountCalculatorTest {

    @Test
    void intraStateGstSplitsCgstAndSgst() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 0, 0, 18, "Karnataka", "Karnataka");
        assertEquals(900, totals.cgstMinor());
        assertEquals(900, totals.sgstMinor());
        assertEquals(0, totals.igstMinor());
        assertEquals(11_800, totals.totalMinor());
    }

    @Test
    void interStateGstUsesIgstOnly() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 0, 0, 18, "Maharashtra", "Karnataka");
        assertEquals(0, totals.cgstMinor());
        assertEquals(0, totals.sgstMinor());
        assertEquals(1_800, totals.igstMinor());
        assertEquals(11_800, totals.totalMinor());
    }

    @Test
    void discountReducesTaxableBase() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 2_000, 0, 18, "Karnataka", "Karnataka");
        assertEquals(9_440, totals.totalMinor());
    }

    @Test
    void percentageDiscountComputedFromSubtotal() {
        long discount = CommerceAmountCalculator.computeDiscountAmount(
                DiscountType.PERCENTAGE, 10, null, 5_000);
        assertEquals(500, discount);
    }
}
