package com.prabhix.platform.commerce.service;

import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.billing.service.BillingAmountCalculator.TaxBreakdown;
import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;

/** Server-side money and GST calculations for storefront orders. Amounts are never taken from client input. */
public final class CommerceAmountCalculator {

    public record OrderTotals(
            long subtotalPaise,
            long discountPaise,
            long taxablePaise,
            long cgstPaise,
            long sgstPaise,
            long igstPaise,
            long shippingPaise,
            long totalPaise) {
    }

    public static long lineTotal(long unitPricePaise, int quantity) {
        return unitPricePaise * quantity;
    }

    public static long computeDiscountAmount(
            DiscountType type,
            Integer percentage,
            Long fixedPaise,
            long subtotalPaise) {
        if (type == DiscountType.PERCENTAGE) {
            int pct = percentage == null ? 0 : percentage;
            return Math.round(subtotalPaise * (pct / 100.0));
        }
        long fixed = fixedPaise == null ? 0 : fixedPaise;
        return Math.min(fixed, subtotalPaise);
    }

    public static long computeShippingPaise(
            long subtotalAfterDiscount,
            long flatShippingPaise,
            Long freeShippingAbovePaise) {
        if (freeShippingAbovePaise != null && subtotalAfterDiscount >= freeShippingAbovePaise) {
            return 0;
        }
        return flatShippingPaise;
    }

    public static OrderTotals computeOrderTotals(
            long subtotalPaise,
            long discountPaise,
            long shippingPaise,
            int gstPercent,
            String buyerState,
            String sellerState) {
        long taxable = Math.max(0, subtotalPaise - discountPaise);
        TaxBreakdown tax = BillingAmountCalculator.computeTax(
                taxable, gstPercent, buyerState, sellerState);
        long total = taxable + tax.totalTaxPaise() + shippingPaise;
        return new OrderTotals(
                subtotalPaise,
                discountPaise,
                taxable,
                tax.cgstPaise(),
                tax.sgstPaise(),
                tax.igstPaise(),
                shippingPaise,
                total);
    }

    public static String formatMoneyInr(long paise) {
        return "₹" + String.format("%,.2f", paise / 100.0);
    }
}
