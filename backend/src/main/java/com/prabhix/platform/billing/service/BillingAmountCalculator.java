package com.prabhix.platform.billing.service;

import com.prabhix.platform.billing.domain.BillingPlan;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;

import java.time.LocalDate;

/** Server-side amount and tax calculations. Amounts are never taken from client input. */
public final class BillingAmountCalculator {

    private BillingAmountCalculator() {
    }

    public static long computePlanAmountPaise(BillingPlan plan, int seats) {
        if (seats < plan.getIncludedSeats()) {
            seats = plan.getIncludedSeats();
        }
        if (plan.getMaxSeats() != null && seats > plan.getMaxSeats()) {
            throw ApiException.of(ErrorCode.SEAT_LIMIT_REACHED,
                    "That plan allows at most " + plan.getMaxSeats() + " seats");
        }
        long extraSeats = Math.max(0, seats - plan.getIncludedSeats());
        return plan.getAmountPaise() + extraSeats * plan.getPerSeatPaise();
    }

    public static TaxBreakdown computeTax(long taxablePaise, int gstPercent, String buyerState, String supplierState) {
        long gstPaise = Math.round(taxablePaise * (gstPercent / 100.0));
        boolean intraState = buyerState != null && supplierState != null
                && buyerState.equalsIgnoreCase(supplierState);
        if (intraState) {
            long half = gstPaise / 2;
            long remainder = gstPaise - half * 2;
            return new TaxBreakdown(half + remainder, half, 0, gstPaise);
        }
        return new TaxBreakdown(0, 0, gstPaise, gstPaise);
    }

    public static String financialYear(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endSuffix = (startYear + 1) % 100;
        return startYear + "-" + String.format("%02d", endSuffix);
    }

    public static String formatInvoiceNumber(String prefix, String financialYear, int sequence) {
        return prefix + "/" + financialYear + "/" + String.format("%06d", sequence);
    }

    public record TaxBreakdown(long cgstPaise, long sgstPaise, long igstPaise, long totalTaxPaise) {
    }
}
