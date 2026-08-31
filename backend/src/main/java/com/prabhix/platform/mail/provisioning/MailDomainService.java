package com.prabhix.platform.mail.provisioning;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.dto.DomainDtos;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.common.util.Json;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailDomainService {

    private final MailDomainRepository domainRepository;
    private final PrabhixProperties properties;

    @Transactional(readOnly = true)
    public List<DomainDtos.DomainResponse> list(UUID organizationId) {
        return domainRepository.findByOrganizationIdAndDeletedAtIsNullOrderByDomain(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public DomainDtos.DomainResponse add(UUID organizationId, DomainDtos.CreateDomainRequest request) {
        if (domainRepository.existsByDomainIgnoreCaseAndDeletedAtIsNull(request.domain())) {
            throw ApiException.conflict("That domain is already registered");
        }
        MailDomain domain = new MailDomain();
        domain.setOrganizationId(organizationId);
        domain.setDomain(request.domain().toLowerCase());
        domain.setMode(request.mode() != null ? request.mode() : MailEnums.DomainMode.EXTERNAL_IMAP);
        domain.setVerificationToken(Ids.token(24));
        domain.setDkimSelector("pbx1");
        generateDkimKeys(domain);
        domain.setDnsReport(Json.toJson(DnsRecordBuilder.build(
                domain.getDomain(), domain.getDkimSelector(),
                domain.getDkimPublicKey(), domain.getVerificationToken())));
        return toDto(domainRepository.save(domain));
    }

    @Transactional
    public void remove(UUID organizationId, UUID domainId) {
        MailDomain domain = domainRepository.findByIdAndOrganizationIdAndDeletedAtIsNull(domainId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Domain"));
        domain.setDeletedAt(java.time.Instant.now());
        domain.setStatus(MailEnums.DomainStatus.DISABLED);
        domainRepository.save(domain);
    }

    private void generateDkimKeys(MailDomain domain) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            domain.setDkimPublicKey("-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----");
            String privatePem = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            domain.setDkimPrivateKeyEnc(DkimKeyCipher.encrypt(
                    properties.security().jwt().secret(), privatePem));
        } catch (Exception ex) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.INTERNAL_ERROR,
                    "Could not generate DKIM keys", ex);
        }
    }

    private DomainDtos.DomainResponse toDto(MailDomain d) {
        return new DomainDtos.DomainResponse(
                d.getId(), d.getDomain(), d.getStatus(), d.getMode(), d.isDefault(),
                d.getMxVerifiedAt(), d.getSpfVerifiedAt(), d.getDkimVerifiedAt(),
                d.getDmarcVerifiedAt(), d.getOwnershipVerifiedAt());
    }
}
