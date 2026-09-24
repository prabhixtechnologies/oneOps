package com.prabhix.platform.commerce.service;

import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommerceAmountCalculatorTest {

    @Test
    void intraStateGstSplitsCgstAndSgst() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 0, 0, 18, "Karnataka", "Karnataka");
        assertEquals(900, totals.cgstPaise());
        assertEquals(900, totals.sgstPaise());
        assertEquals(0, totals.igstPaise());
        assertEquals(11_800, totals.totalPaise());
    }

    @Test
    void interStateGstUsesIgstOnly() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 0, 0, 18, "Maharashtra", "Karnataka");
        assertEquals(0, totals.cgstPaise());
        assertEquals(0, totals.sgstPaise());
        assertEquals(1_800, totals.igstPaise());
        assertEquals(11_800, totals.totalPaise());
    }

    @Test
    void discountReducesTaxableBase() {
        var totals = CommerceAmountCalculator.computeOrderTotals(
                10_000, 2_000, 0, 18, "Karnataka", "Karnataka");
        assertEquals(9_440, totals.totalPaise());
    }

    @Test
    void percentageDiscountComputedFromSubtotal() {
        long discount = CommerceAmountCalculator.computeDiscountAmount(
                DiscountType.PERCENTAGE, 10, null, 5_000);
        assertEquals(500, discount);
    }
}
