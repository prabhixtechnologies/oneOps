package com.prabhix.platform.mail.outbound;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDeliveryEvent;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailDeliveryEventRepository;
import com.prabhix.platform.mail.repository.MailOutboxRepository;
import com.prabhix.platform.mail.util.MailTrackingToken;
import com.prabhix.platform.observability.service.StructuredEventLogger;
import com.prabhix.platform.observability.taxonomy.LogEventCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mail/t")
@RequiredArgsConstructor
public class TrackingController {

    private static final byte[] TRANSPARENT_GIF = Base64.getDecoder().decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final PrabhixProperties properties;
    private final MailDeliveryEventRepository deliveryEventRepository;
    private final MailOutboxRepository outboxRepository;
    private final StructuredEventLogger eventLogger;

    @GetMapping("/o/{token}")
    public ResponseEntity<byte[]> trackOpen(@PathVariable String token) {
        verifyAndRecord(token, MailEnums.DeliveryEventType.OPENED);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_GIF)
                .body(TRANSPARENT_GIF);
    }

    @GetMapping("/c/{token}")
    public ResponseEntity<Void> trackClick(@PathVariable String token) {
        String url = verifyAndRecord(token, MailEnums.DeliveryEventType.CLICKED);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url != null ? url : properties.urls().console()))
                .build();
    }

    private String verifyAndRecord(String token, MailEnums.DeliveryEventType type) {
        if (!properties.mail().tracking().enabled()) {
            return null;
        }
        String payload = MailTrackingToken.parsePayload(token);
        String signature = MailTrackingToken.parseSignature(token);
        if (payload == null || !MailTrackingToken.verify(properties.mail().tracking().secret(), payload, signature)) {
            return null;
        }
        String[] parts = payload.split(":", 3);
        if (parts.length < 2) {
            return null;
        }
        UUID outboxId = UUID.fromString(parts[1]);
        String clickedUrl = null;
        if (type == MailEnums.DeliveryEventType.CLICKED && parts.length == 3) {
            clickedUrl = new String(Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        }

        UUID organizationId = outboxRepository.findById(outboxId)
                .map(row -> row.getOrganizationId())
                .orElse(null);

        MailDeliveryEvent event = new MailDeliveryEvent();
        event.setOrganizationId(organizationId);
        event.setOutboxId(outboxId);
        event.setAddress("tracked");
        event.setEventType(type);
        event.setClickedUrl(clickedUrl);
        event.setOccurredAt(Instant.now());
        deliveryEventRepository.save(event);
        eventLogger.log(
                type == MailEnums.DeliveryEventType.OPENED
                        ? LogEventCode.MAIL_TRACKING_OPENED
                        : LogEventCode.MAIL_TRACKING_CLICKED,
                Map.of("outboxId", outboxId));
        return clickedUrl;
    }
}
