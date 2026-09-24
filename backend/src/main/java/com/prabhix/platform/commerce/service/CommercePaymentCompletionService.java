package com.prabhix.platform.commerce.service;

import com.prabhix.platform.billing.razorpay.RazorpaySignature;
import com.prabhix.platform.commerce.domain.CommerceOrder;
import com.prabhix.platform.commerce.domain.CommercePayment;
import com.prabhix.platform.commerce.domain.OrderEvent;
import com.prabhix.platform.commerce.domain.CommerceEnums.OrderStatus;
import com.prabhix.platform.commerce.domain.CommerceEnums.PaymentStatus;
import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.domain.OrderDownload;
import com.prabhix.platform.commerce.domain.OrderItem;
import com.prabhix.platform.commerce.repository.CommerceCustomerRepository;
import com.prabhix.platform.commerce.repository.CommerceOrderRepository;
import com.prabhix.platform.commerce.repository.CommercePaymentRepository;
import com.prabhix.platform.commerce.repository.CommerceSubscriptionRepository;
import com.prabhix.platform.commerce.repository.OrderDownloadRepository;
import com.prabhix.platform.commerce.repository.OrderEventRepository;
import com.prabhix.platform.commerce.repository.OrderItemRepository;
import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.common.event.AuditRequested;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import com.prabhix.platform.common.mail.MailClient;
import com.prabhix.platform.common.mail.MailRequest;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.Organization;
import com.prabhix.platform.org.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for marking a commerce order paid. Both checkout verify and webhooks delegate here.
 */
@Service
@RequiredArgsConstructor
public class CommercePaymentCompletionService {

    private final CommerceOrderRepository orderRepository;
    private final CommercePaymentRepository paymentRepository;
    private final OrderEventRepository eventRepository;
    private final CommerceCustomerRepository customerRepository;
    private final CommerceSubscriptionRepository subscriptionRepository;
    private final StockService stockService;
    private final CommerceInvoiceService invoiceService;
    private final CommerceOrderProvisioningService provisioningService;
    private final CommerceSubscriptionRenewalService subscriptionRenewalService;
    private final OrganizationRepository organizationRepository;
    private final ApplicationEventPublisher events;
    private final MailClient mail;
    private final PrabhixProperties properties;
    private final OrderDownloadRepository downloadRepository;
    private final OrderItemRepository orderItemRepository;
    private final StructuredEventLogger eventLogger;

    public record CaptureDetails(
            String razorpayPaymentId,
            long amountPaise,
            String currency,
            String method,
            boolean signatureVerified) {
    }

    @Transactional
    public CommerceOrder completeCapture(CommerceOrder order, CaptureDetails capture) {
        order = orderRepository.lockById(order.getId())
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FULFILLED) {
            ensureInvoice(order);
            return order;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.of(ErrorCode.ORDER_NOT_PAYABLE, "That order cannot be paid");
        }

        order.setStatus(OrderStatus.PAID);
        order.setRazorpayPaymentId(capture.razorpayPaymentId());
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        upsertPayment(order, capture);
        stockService.commitForOrder(order);
        if (order.getRenewalSubscriptionId() != null) {
            subscriptionRepository.findById(order.getRenewalSubscriptionId())
                    .ifPresent(subscriptionRenewalService::extendSubscriptionPeriod);
        } else {
            provisioningService.provisionPaidOrder(order);
        }
        var invoice = invoiceService.issueForOrder(order);
        order.setInvoiceId(invoice.getId());
        orderRepository.save(order);

        appendEvent(order, "PAYMENT_CAPTURED", "Payment received");
        sendConfirmationMail(order);
        events.publishEvent(AuditRequested.labelled(
                order.getOrganizationId(), null,
                "commerce.order.paid", "commerce_order", order.getId(), order.getOrderNumber()));
        eventLogger.log(LogEventCode.COMMERCE_ORDER_PAID, Map.of(
                "orderId", order.getId(), "orderNumber", order.getOrderNumber()));

        return order;
    }

    @Transactional
    public CommerceOrder verifyCheckoutPayment(UUID organizationId, CommerceDtos.VerifyPaymentRequest request) {
        if (!RazorpaySignature.verifyCheckout(
                request.razorpayOrderId(),
                request.razorpayPaymentId(),
                request.razorpaySignature(),
                properties.billing().razorpay().keySecret())) {
            throw ApiException.of(ErrorCode.PAYMENT_SIGNATURE_MISMATCH, "Payment signature did not match");
        }
        CommerceOrder order = orderRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .filter(o -> o.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> ApiException.of(ErrorCode.ORDER_NOT_FOUND, "That order was not found"));
        return completeCapture(order, new CaptureDetails(
                request.razorpayPaymentId(),
                order.getTotalPaise(),
                order.getCurrency(),
                null,
                true));
    }

    private void upsertPayment(CommerceOrder order, CaptureDetails capture) {
        CommercePayment payment = paymentRepository.findByOrderIdAndOrganizationId(
                        order.getId(), order.getOrganizationId())
                .orElseGet(() -> {
                    CommercePayment created = new CommercePayment();
                    created.setOrganizationId(order.getOrganizationId());
                    created.setOrderId(order.getId());
                    return created;
                });
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return;
        }
        payment.setRazorpayPaymentId(capture.razorpayPaymentId());
        payment.setRazorpayOrderId(order.getRazorpayOrderId());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setAmountPaise(capture.amountPaise());
        payment.setCurrency(capture.currency());
        payment.setMethod(capture.method());
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);
    }

    private void ensureInvoice(CommerceOrder order) {
        invoiceService.issueForOrder(order);
    }

    private void sendConfirmationMail(CommerceOrder order) {
        customerRepository.findById(order.getCustomerId()).ifPresent(customer -> {
            Organization org = organizationRepository.findById(order.getOrganizationId()).orElse(null);
            String orgName = org == null ? "Store" : org.getName();
            mail.send(MailRequest.forOrganization(
                    order.getOrganizationId(),
                    customer.getEmail(),
                    "commerce.order-confirmation",
                    Map.of(
                            "organizationName", orgName,
                            "orderNumber", order.getOrderNumber(),
                            "orderTotal", CommerceAmountCalculator.formatMoneyInr(order.getTotalPaise()),
                            "orderUrl", orderClaimUrl(order),
                            "downloadSection", downloadSectionHtml(order)),
                    "commerce-order-" + order.getId()));
        });
    }

    private String marketingBase() {
        PrabhixProperties.Urls urls = properties.urls();
        String base = urls == null || urls.marketing() == null || urls.marketing().isBlank()
                ? "http://localhost:3000"
                : urls.marketing();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String orderClaimUrl(CommerceOrder order) {
        return marketingBase() + "/shop/order/claim/" + order.getAccessToken();
    }

    private String downloadSectionHtml(CommerceOrder order) {
        var downloads = downloadRepository.findByOrderIdAndOrganizationId(
                order.getId(), order.getOrganizationId());
        if (downloads.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<p style=\"font-size:15px;line-height:1.6;margin:0 0 8px\">Your downloads</p><ul>");
        for (OrderDownload download : downloads) {
            String name = orderItemRepository.findById(download.getOrderItemId())
                    .map(OrderItem::getProductName)
                    .orElse("File");
            html.append("<li style=\"margin:0 0 8px\"><a href=\"")
                    .append(marketingBase())
                    .append("/download/")
                    .append(download.getDownloadToken())
                    .append("\" style=\"color:#0e7490\">")
                    .append(escapeHtml(name))
                    .append("</a></li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void appendEvent(CommerceOrder order, String type, String message) {
        OrderEvent event = new OrderEvent();
        event.setOrganizationId(order.getOrganizationId());
        event.setOrderId(order.getId());
        event.setEventType(type);
        event.setMessage(message);
        eventRepository.save(event);
    }
}
