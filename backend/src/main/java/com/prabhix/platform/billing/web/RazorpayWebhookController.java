package com.prabhix.platform.billing.web;

import com.prabhix.platform.billing.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oneops/billing/webhooks")
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final WebhookService webhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestBody byte[] rawBody) {
        webhookService.receive(signature, eventId, rawBody);
        return ResponseEntity.ok().build();
    }
}
