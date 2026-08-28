package com.prabhix.platform.mail.inbound;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailAlias;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.domain.Mailbox;
import com.prabhix.platform.mail.dto.InboundDtos;
import com.prabhix.platform.mail.repository.MailAliasRepository;
import com.prabhix.platform.mail.repository.MailboxRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/mail/inbound")
@RequiredArgsConstructor
public class LmtpIngestController {

    private final PrabhixProperties properties;
    private final MailboxRepository mailboxRepository;
    private final MailAliasRepository aliasRepository;
    private final MailIngestionService ingestionService;

    @PostMapping("/lmtp")
    public ResponseEntity<InboundDtos.LmtpResponse> ingest(
            @RequestHeader(value = "X-Mail-Token", required = false) String token,
            @Valid @RequestBody InboundDtos.LmtpRequest request) {
        String configured = properties.mail().inbound().lmtpToken();
        if (configured == null || configured.isBlank()) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "LMTP ingestion is not configured");
        }
        if (!constantTimeEquals(token, configured)) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Invalid LMTP token");
        }

        Mailbox mailbox = resolveMailbox(request.recipient());
        byte[] raw = Base64.getDecoder().decode(request.rawMimeBase64());
        var staged = ingestionService.stageRaw(
                mailbox.getOrganizationId(), mailbox.getId(),
                MailEnums.InboundSource.LMTP, null, raw);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new InboundDtos.LmtpResponse(staged.getId()));
    }

    private Mailbox resolveMailbox(String recipient) {
        return mailboxRepository.findByAddressIgnoreCaseAndDeletedAtIsNull(recipient)
                .or(() -> aliasRepository.findByAddressIgnoreCase(recipient)
                        .map(MailAlias::getMailboxId)
                        .flatMap(mailboxRepository::findById))
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_FOUND,
                        "No mailbox for recipient " + recipient));
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
