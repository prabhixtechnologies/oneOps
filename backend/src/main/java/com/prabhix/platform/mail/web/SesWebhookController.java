package com.prabhix.platform.mail.web;

import com.prabhix.platform.mail.outbound.SesWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mail/webhooks")
@RequiredArgsConstructor
public class SesWebhookController {

    private final SesWebhookService webhookService;

    @PostMapping("/ses")
    public ResponseEntity<Void> handle(@RequestBody byte[] rawBody) {
        webhookService.receive(rawBody);
        return ResponseEntity.ok().build();
    }
}
