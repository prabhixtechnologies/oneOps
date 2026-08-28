package com.prabhix.platform.billing.web;

import com.prabhix.platform.billing.dto.BillingDtos.BillingAddressView;
import com.prabhix.platform.billing.dto.BillingDtos.BillingChangeResponse;
import com.prabhix.platform.billing.dto.BillingDtos.CancelSubscriptionRequest;
import com.prabhix.platform.billing.dto.BillingDtos.ChangePlanRequest;
import com.prabhix.platform.billing.dto.BillingDtos.ChangeSeatsRequest;
import com.prabhix.platform.billing.dto.BillingDtos.CreateOrderRequest;
import com.prabhix.platform.billing.dto.BillingDtos.InvoiceDownload;
import com.prabhix.platform.billing.dto.BillingDtos.InvoiceSummary;
import com.prabhix.platform.billing.dto.BillingDtos.OrderView;
import com.prabhix.platform.billing.dto.BillingDtos.PaymentMethodView;
import com.prabhix.platform.billing.dto.BillingDtos.PlanView;
import com.prabhix.platform.billing.dto.BillingDtos.RefundRequest;
import com.prabhix.platform.billing.dto.BillingDtos.RefundView;
import com.prabhix.platform.billing.dto.BillingDtos.SubscriptionView;
import com.prabhix.platform.billing.dto.BillingDtos.UpdateBillingAddressRequest;
import com.prabhix.platform.billing.dto.BillingDtos.VerifyPaymentRequest;
import com.prabhix.platform.billing.dto.BillingDtos.VerifyPaymentResponse;
import com.prabhix.platform.billing.service.BillingAddressService;
import com.prabhix.platform.billing.service.BillingService;
import com.prabhix.platform.billing.service.EntitlementService;
import com.prabhix.platform.billing.service.InvoiceService;
import com.prabhix.platform.billing.service.PaymentMethodService;
import com.prabhix.platform.billing.service.RefundService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.common.web.PageResponse;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;
    private final InvoiceService invoiceService;
    private final EntitlementService entitlementService;
    private final BillingAddressService billingAddressService;
    private final PaymentMethodService paymentMethodService;
    private final RefundService refundService;
    private final PrabhixProperties properties;

    @GetMapping("/plans")
    @PreAuthorize(Authorize.BILLING_READ)
    public PageResponse<PlanView> listPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        int pageSize = properties.limits().clampPageSize(size);
        return billingService.listPlans(page, pageSize);
    }

    @GetMapping("/subscription")
    @PreAuthorize(Authorize.BILLING_READ)
    public SubscriptionView subscription(@CurrentUser PrabhixPrincipal principal) {
        return billingService.getSubscription(principal.requireOrganizationId());
    }

    @GetMapping("/entitlements")
    @PreAuthorize(Authorize.BILLING_READ)
    public Map<String, Object> entitlements(@CurrentUser PrabhixPrincipal principal) {
        return entitlementService.entitlementsFor(principal.requireOrganizationId());
    }

    @PostMapping("/orders")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public OrderView createOrder(@CurrentUser PrabhixPrincipal principal,
                                 @Valid @RequestBody CreateOrderRequest request) {
        return billingService.createOrder(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @PostMapping("/orders/{id}/dev-complete")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public OrderView completeDevOrder(@CurrentUser PrabhixPrincipal principal,
                                      @PathVariable UUID id) {
        return billingService.completeDevOrder(principal.requireOrganizationId(), id);
    }

    @PostMapping("/verify")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public VerifyPaymentResponse verify(@CurrentUser PrabhixPrincipal principal,
                                          @Valid @RequestBody VerifyPaymentRequest request) {
        return billingService.verifyCheckout(principal.requireOrganizationId(), request);
    }

    @PostMapping("/subscription/change-plan")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public BillingChangeResponse changePlan(@CurrentUser PrabhixPrincipal principal,
                                            @Valid @RequestBody ChangePlanRequest request) {
        return billingService.changePlan(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @PostMapping("/subscription/seats")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public BillingChangeResponse changeSeats(@CurrentUser PrabhixPrincipal principal,
                                             @Valid @RequestBody ChangeSeatsRequest request) {
        return billingService.changeSeats(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @PostMapping("/subscription/cancel")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public SubscriptionView cancel(@CurrentUser PrabhixPrincipal principal,
                                   @Valid @RequestBody CancelSubscriptionRequest request) {
        return billingService.cancel(
                principal.requireOrganizationId(), principal.userId(), request);
    }

    @PostMapping("/subscription/reactivate")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public SubscriptionView reactivate(@CurrentUser PrabhixPrincipal principal) {
        return billingService.reactivate(principal.requireOrganizationId(), principal.userId());
    }

    @GetMapping("/invoices")
    @PreAuthorize(Authorize.BILLING_READ)
    public CursorPage<InvoiceSummary> invoices(@CurrentUser PrabhixPrincipal principal,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit) {
        int pageSize = properties.limits().clampPageSize(limit);
        return invoiceService.listInvoices(principal.requireOrganizationId(), cursor, pageSize);
    }

    @GetMapping("/invoices/{id}/download")
    @PreAuthorize(Authorize.BILLING_INVOICE_DOWNLOAD)
    public ResponseEntity<byte[]> downloadInvoice(@CurrentUser PrabhixPrincipal principal,
                                                  @PathVariable UUID id) {
        InvoiceDownload download = invoiceService.download(principal.requireOrganizationId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.invoiceNumber() + ".pdf\"")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .body(download.content());
    }

    @GetMapping("/payment-methods")
    @PreAuthorize(Authorize.BILLING_READ)
    public List<PaymentMethodView> paymentMethods(@CurrentUser PrabhixPrincipal principal) {
        return paymentMethodService.listMethods(principal.requireOrganizationId());
    }

    @GetMapping("/address")
    @PreAuthorize(Authorize.BILLING_READ)
    public BillingAddressView billingAddress(@CurrentUser PrabhixPrincipal principal) {
        return billingAddressService.getAddress(principal.requireOrganizationId());
    }

    @PutMapping("/address")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public BillingAddressView updateBillingAddress(@CurrentUser PrabhixPrincipal principal,
                                                   @Valid @RequestBody UpdateBillingAddressRequest request) {
        return billingAddressService.updateAddress(principal.requireOrganizationId(), request);
    }

    @PostMapping("/refunds")
    @PreAuthorize(Authorize.BILLING_MANAGE)
    public RefundView refund(@CurrentUser PrabhixPrincipal principal,
                             @Valid @RequestBody RefundRequest request) {
        return refundService.refund(
                principal.requireOrganizationId(), principal.userId(), request);
    }
}
