package com.prabhix.platform.billing.dto;

import com.prabhix.platform.billing.domain.BillingEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BillingDtos {

    private BillingDtos() {
    }

    public record PlanView(
            UUID id,
            String planKey,
            String name,
            String description,
            BillingEnums.PlanInterval intervalType,
            long amountPaise,
            long perSeatPaise,
            int includedSeats,
            Integer maxSeats,
            int trialDays,
            Map<String, Object> entitlements) {
    }

    public record SubscriptionView(
            UUID id,
            UUID planId,
            String planName,
            BillingEnums.SubscriptionStatus status,
            int seats,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt,
            boolean cancelAtPeriodEnd,
            Instant nextBillingAt,
            long lockedAmountPaise,
            long lockedPerSeatPaise,
            UUID pendingPlanId,
            Integer pendingSeats,
            Instant pendingChangeAt) {
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 60) String planKey,
            @NotNull @Min(1) @Max(10000) Integer seats) {
    }

    public record OrderView(
            UUID id,
            String razorpayOrderId,
            long amountPaise,
            long taxPaise,
            long totalPaise,
            String currency,
            BillingEnums.OrderStatus status,
            String receipt,
            boolean localDevCheckout) {
    }

    public record VerifyPaymentRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }

    public record VerifyPaymentResponse(
            UUID orderId,
            BillingEnums.OrderStatus status,
            boolean signatureVerified) {
    }

    public record ChangePlanRequest(
            @NotBlank @Size(max = 60) String planKey,
            @NotNull @Min(1) @Max(10000) Integer seats) {
    }

    public record ChangeSeatsRequest(
            @NotNull @Min(1) @Max(10000) Integer seats) {
    }

    public record BillingChangeResponse(
            SubscriptionView subscription,
            OrderView checkoutOrder,
            String message) {
    }

    public record CancelSubscriptionRequest(
            boolean atPeriodEnd,
            @Size(max = 500) String reason) {
    }

    public record InvoiceSummary(
            UUID id,
            String invoiceNumber,
            BillingEnums.InvoiceStatus status,
            LocalDate issueDate,
            long totalPaise,
            String currency,
            Instant paidAt) {
    }

    public record InvoiceDownload(
            UUID invoiceId,
            String invoiceNumber,
            String contentType,
            byte[] content) {
    }

    public record BillingAddressView(
            String line1,
            String line2,
            String city,
            String state,
            String pincode,
            String country,
            String gstin,
            String billingEmail) {
    }

    public record UpdateBillingAddressRequest(
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 12) String pincode,
            @Size(max = 2) String country,
            @Size(max = 15) String gstin,
            @Size(max = 254) String billingEmail) {
    }

    public record PaymentMethodView(
            String method,
            String label,
            Instant lastUsedAt,
            boolean vaulted) {
    }

    public record RefundRequest(
            @NotNull UUID paymentId,
            Long amountPaise) {
    }

    public record RefundView(
            UUID paymentId,
            long refundedPaise,
            long totalRefundedPaise,
            BillingEnums.PaymentStatus status) {
    }
}
