package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.dto.CommerceDtos;
import com.prabhix.platform.commerce.service.CommerceOrderService;
import com.prabhix.platform.commerce.service.CommerceRefundService;
import com.prabhix.platform.commerce.service.DownloadService;
import com.prabhix.platform.common.web.CursorPage;
import com.prabhix.platform.security.CurrentUser;
import com.prabhix.platform.security.PrabhixPrincipal;
import com.prabhix.platform.security.rbac.Authorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oneops/commerce/orders")
@RequiredArgsConstructor
public class CommerceOrderController {

    private final CommerceOrderService orderService;
    private final CommerceRefundService refundService;
    private final DownloadService downloadService;

    @GetMapping
    @PreAuthorize(Authorize.COMMERCE_ORDER_READ)
    public CursorPage<CommerceDtos.OrderSummary> list(
            @CurrentUser PrabhixPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return orderService.list(principal.requireOrganizationId(), status, from, to, search, cursor, limit);
    }

    @GetMapping(params = "id")
    @PreAuthorize(Authorize.COMMERCE_ORDER_READ)
    public CommerceDtos.OrderDetail get(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return orderService.get(principal.requireOrganizationId(), id);
    }

    @PostMapping("/fulfill")
    @PreAuthorize(Authorize.COMMERCE_ORDER_MANAGE)
    public CommerceDtos.OrderDetail fulfill(@CurrentUser PrabhixPrincipal principal,
                                            @RequestParam UUID id,
                                            @Valid @RequestBody(required = false) CommerceDtos.FulfillOrderRequest request) {
        CommerceDtos.FulfillOrderRequest body = request == null
                ? new CommerceDtos.FulfillOrderRequest(null, null) : request;
        return orderService.fulfill(principal, id, body);
    }

    @PostMapping("/cancel")
    @PreAuthorize(Authorize.COMMERCE_ORDER_MANAGE)
    public CommerceDtos.OrderDetail cancel(@CurrentUser PrabhixPrincipal principal, @RequestParam UUID id) {
        return orderService.cancel(principal, id);
    }

    @PatchMapping
    @PreAuthorize(Authorize.COMMERCE_ORDER_MANAGE)
    public CommerceDtos.OrderDetail annotate(@CurrentUser PrabhixPrincipal principal,
                                             @RequestParam UUID id,
                                             @Valid @RequestBody CommerceDtos.UpdateOrderRequest request) {
        return orderService.annotate(principal, id, request);
    }

    @PostMapping("/refunds")
    @PreAuthorize(Authorize.COMMERCE_ORDER_REFUND)
    public CommerceDtos.RefundView refund(@CurrentUser PrabhixPrincipal principal,
                                          @Valid @RequestBody CommerceDtos.RefundRequest request) {
        return refundService.refund(principal, request);
    }

    @GetMapping("/downloads")
    @PreAuthorize(Authorize.COMMERCE_ORDER_READ)
    public List<CommerceDtos.DownloadView> downloads(@CurrentUser PrabhixPrincipal principal,
                                                     @RequestParam UUID id) {
        return downloadService.listForOrder(principal.requireOrganizationId(), id);
    }

    @PostMapping("/downloads/reissue")
    @PreAuthorize(Authorize.COMMERCE_ORDER_MANAGE)
    public CommerceDtos.DownloadLinkResponse reissueDownload(@CurrentUser PrabhixPrincipal principal,
                                                             @RequestParam UUID orderId,
                                                             @RequestParam UUID orderItemId) {
        return downloadService.reissue(principal.requireOrganizationId(), orderId, orderItemId);
    }
}
