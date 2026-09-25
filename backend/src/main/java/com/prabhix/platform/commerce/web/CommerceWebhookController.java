package com.prabhix.platform.commerce.web;

import com.prabhix.platform.commerce.service.CommerceWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/oneops/commerce/webhooks")
@RequiredArgsConstructor
public class CommerceWebhookController {

    private final CommerceWebhookService webhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handle(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId,
            @RequestBody byte[] rawBody) {
        webhookService.receive(signature, eventId, rawBody);
        return ResponseEntity.ok().build();
    }
}
