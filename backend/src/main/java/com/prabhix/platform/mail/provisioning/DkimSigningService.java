package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DkimSigningService {

    private final MailDomainRepository domainRepository;
    private final PrabhixProperties properties;

    public void signIfApplicable(MimeMessage message, UUID organizationId, String fromAddress) {
        if (organizationId == null || fromAddress == null || !fromAddress.contains("@")) {
            return;
        }
        String domainName = fromAddress.substring(fromAddress.indexOf('@') + 1).toLowerCase();
        MailDomain domain = domainRepository.findByDomainIgnoreCaseAndDeletedAtIsNull(domainName)
                .filter(d -> organizationId.equals(d.getOrganizationId()))
                .filter(d -> d.getDkimVerifiedAt() != null)
                .filter(d -> d.getDkimPrivateKeyEnc() != null && !d.getDkimPrivateKeyEnc().isBlank())
                .orElse(null);
        if (domain == null) {
            return;
        }

        try {
            String decrypted = DkimKeyCipher.decrypt(
                    properties.security().internalSigningSecret(), domain.getDkimPrivateKeyEnc());
            PrivateKey privateKey = DkimSigner.loadPrivateKey(decrypted);

            Map<String, String> headers = new LinkedHashMap<>();
            if (message.getHeader("From") != null) {
                headers.put("From", message.getHeader("From")[0]);
            }
            if (message.getHeader("To") != null) {
                headers.put("To", String.join(", ", message.getHeader("To")));
            }
            if (message.getHeader("Subject") != null) {
                headers.put("Subject", message.getHeader("Subject")[0]);
            }
            if (message.getHeader("Date") != null) {
                headers.put("Date", message.getHeader("Date")[0]);
            }
            if (message.getHeader("MIME-Version") != null) {
                headers.put("MIME-Version", message.getHeader("MIME-Version")[0]);
            }
            if (message.getHeader("Content-Type") != null) {
                headers.put("Content-Type", message.getHeader("Content-Type")[0]);
            }

            ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
            if (message.getContent() != null) {
                message.writeTo(bodyStream);
            }
            byte[] raw = bodyStream.toByteArray();
            int bodyStart = indexOfBody(raw);
            byte[] bodyBytes = bodyStart >= 0 ? java.util.Arrays.copyOfRange(raw, bodyStart, raw.length) : raw;

            String signature = DkimSigner.sign(headers, bodyBytes, privateKey,
                    domain.getDomain(), domain.getDkimSelector());
            message.setHeader("DKIM-Signature", signature);
        } catch (Exception ex) {
            log.warn("DKIM signing failed for {}: {}", fromAddress, ex.getMessage());
        }
    }

    private static int indexOfBody(byte[] raw) {
        for (int i = 0; i < raw.length - 3; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n' && raw[i + 2] == '\r' && raw[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }
}
