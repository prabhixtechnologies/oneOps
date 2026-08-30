package com.prabhix.platform.commerce.service;

import tools.jackson.databind.JsonNode;
import com.prabhix.platform.billing.razorpay.RazorpayClient;
import com.prabhix.platform.billing.service.BillingAmountCalculator;
import com.prabhix.platform.billing.service.DunningSchedule;
import com.prabhix.platform.commerce.domain.CommerceCustomer;
import com.prabhix.platform.commerce.domain.CommerceEnums;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommercePayment;
import com.prabhix.platform.commerce.domain.CommerceSettings;
import com.prabhix.platform.commerce.domain.CommerceSubscription;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommercePaymentRepository;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Storefront subscription renewals mirror SaaS billing: self-managed orders plus saved-token
 * charges rather than Razorpay subscription entities.
 */
@Slf4j
@Service
public class CommerceSubscriptionRenewalService {

    private final CommerceSubscriptionRepository subscriptionRepository;
    private final CommerceOrderRepository orderRepository;
    private final CommercePaymentRepository paymentRepository;
    private final CommerceCustomerRepository customerRepository;
    private final CommerceSettingsService settingsService;
    private final OrderNumberService orderNumberService;
    private final RazorpayClient razorpayClient;
    private final CommercePaymentCompletionService paymentCompletionService;
    private final PrabhixProperties properties;

    public CommerceSubscriptionRenewalService(
            CommerceSubscriptionRepository subscriptionRepository,
            CommerceOrderRepository orderRepository,
            CommercePaymentRepository paymentRepository,
            CommerceCustomerRepository customerRepository,
            CommerceSettingsService settingsService,
            OrderNumberService orderNumberService,
            RazorpayClient razorpayClient,
            @Lazy CommercePaymentCompletionService paymentCompletionService,
            PrabhixProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.settingsService = settingsService;
        this.orderNumberService = orderNumberService;
        this.razorpayClient = razorpayClient;
        this.paymentCompletionService = paymentCompletionService;
        this.properties = properties;
    }

    public record ChargeResult(boolean charged) {
    }

    @Transactional
    public CommerceOrder createRenewalOrder(CommerceSubscription subscription) {
        subscription = subscriptionRepository.lockById(subscription.getId()).orElse(subscription);
        CommerceSettings settings = settingsService.resolve(subscription.getOrganizationId());
        CommerceCustomer customer = customerRepository.findById(subscription.getCustomerId()).orElseThrow();

        LocalDate today = LocalDate.now();
        String fy = BillingAmountCalculator.financialYear(today);
        int sequence = orderNumberService.nextSequence(subscription.getOrganizationId(), fy);
        String orderNumber = BillingAmountCalculator.formatInvoiceNumber(
                settings.getOrderNumberPrefix(), fy, sequence);

        long subtotal = subscription.getLockedPriceMinor();
        var totals = CommerceAmountCalculator.computeOrderTotals(
                subtotal, 0, 0, settings.getGstPercent(),
                null, settings.getSellerState());

        CommerceOrder order = new CommerceOrder();
        order.setOrganizationId(subscription.getOrganizationId());
        order.setOrderNumber(orderNumber);
        order.setFinancialYear(fy);
        order.setSequenceNumber(sequence);
        order.setStatus(CommerceEnums.OrderStatus.PENDING_PAYMENT);
        order.setCustomerId(customer.getId());
        order.setAccessToken(CommerceTokens.opaqueToken());
        order.setCurrency(subscription.getCurrency());
        order.setSubtotalMinor(totals.subtotalMinor());
        order.setDiscountMinor(0);
        order.setCgstMinor(totals.cgstMinor());
        order.setSgstMinor(totals.sgstMinor());
        order.setIgstMinor(totals.igstMinor());
        order.setShippingMinor(0);
        order.setTotalMinor(totals.totalMinor());
        order.setSellerState(settings.getSellerState());
        order.setGstPercent(settings.getGstPercent());
        order.setSubscriptionCheckout(true);
        order.setRenewalSubscriptionId(subscription.getId());
        order = orderRepository.save(order);

        CommercePayment payment = new CommercePayment();
        payment.setOrganizationId(subscription.getOrganizationId());
        payment.setOrderId(order.getId());
        payment.setStatus(CommerceEnums.PaymentStatus.INITIATED);
        payment.setAmountMinor(order.getTotalMinor());
        payment.setCurrency(order.getCurrency());
        paymentRepository.save(payment);

        if (properties.billing().razorpay().configured()) {
            JsonNode gatewayOrder = razorpayClient.createOrder(
                    order.getTotalMinor(),
                    order.getCurrency(),
                    order.getOrderNumber(),
                    Map.of(
                            "commerce_order_id", order.getId().toString(),
                            "commerce_subscription_id", subscription.getId().toString(),
                            "renewal", "true"));
            order.setRazorpayOrderId(gatewayOrder.path("id").asText());
            orderRepository.save(order);
        }
        return order;
    }

    @Transactional
    public ChargeResult attemptOffSessionCharge(CommerceSubscription subscription, CommerceOrder order) {
        if (!properties.billing().razorpay().configured()) {
            return new ChargeResult(false);
        }
        if (subscription.getRazorpayTokenId() == null || subscription.getRazorpayTokenId().isBlank()) {
            return new ChargeResult(false);
        }
        CommerceCustomer customer = customerRepository.findById(subscription.getCustomerId()).orElse(null);
        if (customer == null || customer.getEmail() == null) {
            return new ChargeResult(false);
        }
        try {
            JsonNode payment = razorpayClient.createRecurringPayment(
                    customer.getEmail(),
                    customer.getPhone(),
                    order.getTotalMinor(),
                    order.getCurrency(),
                    order.getRazorpayOrderId(),
                    null,
                    subscription.getRazorpayTokenId());
            String status = payment.path("status").asText();
            if ("captured".equals(status) || "authorized".equals(status)) {
                paymentCompletionService.completeCapture(order, new CommercePaymentCompletionService.CaptureDetails(
                        payment.path("id").asText(),
                        order.getTotalMinor(),
                        order.getCurrency(),
                        payment.path("method").asText(null),
                        false));
                return new ChargeResult(true);
            }
            return new ChargeResult(false);
        } catch (Exception ex) {
            log.warn("Commerce subscription renewal charge failed for {}: {}",
                    subscription.getId(), ex.getMessage());
            return new ChargeResult(false);
        }
    }

    @Transactional
    public void extendSubscriptionPeriod(CommerceSubscription subscription) {
        Instant periodStart = subscription.getCurrentPeriodEnd();
        subscription.setCurrentPeriodStart(periodStart);
        subscription.setCurrentPeriodEnd(periodStart.plus(
                CommerceOrderProvisioningService.periodDays(subscription.getBillingInterval()),
                ChronoUnit.DAYS));
        subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
        subscription.setStatus(CommerceEnums.SubscriptionStatus.ACTIVE);
        subscription.setFailedPaymentCount(0);
        subscription.setNextDunningRetryAt(null);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void markPastDue(CommerceSubscription subscription) {
        subscription.setStatus(CommerceEnums.SubscriptionStatus.PAST_DUE);
        subscription.setFailedPaymentCount(Math.max(1, subscription.getFailedPaymentCount()));
        subscription.setNextDunningRetryAt(DunningSchedule.initialGraceEnd());
        subscriptionRepository.save(subscription);
    }
}
