package com.prabhix.platform.commerce.service;

import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.billing.service.BillingAmountCalculator.TaxBreakdown;
import com.prabhix.platform.commerce.domain.CommerceEnums.DiscountType;

/** Server-side money and GST calculations for storefront orders. Amounts are never taken from client input. */
public final class CommerceAmountCalculator {

    public record OrderTotals(
            long subtotalMinor,
            long discountMinor,
            long taxableMinor,
            long cgstMinor,
            long sgstMinor,
            long igstMinor,
            long shippingMinor,
            long totalMinor) {
    }

    public static long lineTotal(long unitPriceMinor, int quantity) {
        return unitPriceMinor * quantity;
    }

    public static long computeDiscountAmount(
            DiscountType type,
            Integer percentage,
            Long fixedMinor,
            long subtotalMinor) {
        if (type == DiscountType.PERCENTAGE) {
            int pct = percentage == null ? 0 : percentage;
            return Math.round(subtotalMinor * (pct / 100.0));
        }
        long fixed = fixedMinor == null ? 0 : fixedMinor;
        return Math.min(fixed, subtotalMinor);
    }

    public static long computeShippingMinor(
            long subtotalAfterDiscount,
            long flatShippingMinor,
            Long freeShippingAboveMinor) {
        if (freeShippingAboveMinor != null && subtotalAfterDiscount >= freeShippingAboveMinor) {
            return 0;
        }
        return flatShippingMinor;
    }

    public static OrderTotals computeOrderTotals(
            long subtotalMinor,
            long discountMinor,
            long shippingMinor,
            int gstPercent,
            String buyerState,
            String sellerState) {
        long taxable = Math.max(0, subtotalMinor - discountMinor);
        TaxBreakdown tax = BillingAmountCalculator.computeTax(
                taxable, gstPercent, buyerState, sellerState);
        long total = taxable + tax.totalTaxPaise() + shippingMinor;
        return new OrderTotals(
                subtotalMinor,
                discountMinor,
                taxable,
                tax.cgstPaise(),
                tax.sgstPaise(),
                tax.igstPaise(),
                shippingMinor,
                total);
    }

    public static String formatMoneyInr(long minor) {
        return "₹" + String.format("%,.2f", minor / 100.0);
    }
}
