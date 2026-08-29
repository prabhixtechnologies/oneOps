package com.prabhix.platform.mail.mailbox;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.mail.domain.MailAlias;
import com.prabhix.platform.mail.domain.MailDomain;
import com.prabhix.platform.mail.domain.MailEnums;
import com.prabhix.platform.mail.repository.MailAliasRepository;
import com.prabhix.platform.mail.repository.MailDomainRepository;
import com.prabhix.platform.security.PrabhixPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Extra addresses that deliver into a mailbox.
 *
 * <p>The table has been read by inbound routing and by reply-all since V5, and there has never been a way
 * to put a row in it. This is that way.
 *
 * <p>An alias may only be created inside a domain this organization has verified. Without that check the
 * global uniqueness of {@code mail_aliases.address} becomes a land grab: one tenant could claim an
 * address in another tenant's domain and quietly divert its mail.
 */
@Service
@RequiredArgsConstructor
public class MailAliasService {

    private static final int MAX_PER_MAILBOX = 25;

    private final MailAliasRepository aliasRepository;
    private final MailDomainRepository domainRepository;
    private final MailboxAccess access;

    @Transactional(readOnly = true)
    public List<MailboxDtos.AliasView> list(PrabhixPrincipal principal, UUID mailboxId) {
        access.requireMailbox(principal, mailboxId);
        return aliasRepository.findByMailboxIdAndOrganizationId(
                        mailboxId, principal.requireOrganizationId())
                .stream()
                .map(a -> new MailboxDtos.AliasView(a.getId(), a.getMailboxId(), a.getAddress(),
                        a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MailboxDtos.AliasView create(PrabhixPrincipal principal, UUID mailboxId,
                                        MailboxDtos.CreateAliasRequest request) {
        UUID orgId = principal.requireOrganizationId();
        access.requireMailbox(principal, mailboxId);

        String address = request.address().trim().toLowerCase(Locale.ROOT);
        int at = address.lastIndexOf('@');
        if (at <= 0 || at == address.length() - 1) {
            throw ApiException.invalidState("That does not look like an email address");
        }
        String domain = address.substring(at + 1);

        MailDomain owned = domainRepository.findByDomainIgnoreCaseAndDeletedAtIsNull(domain)
                .filter(d -> d.getOrganizationId().equals(orgId))
                .orElseThrow(() -> ApiException.invalidState(
                        "Add and verify " + domain + " before creating addresses on it"));
        if (owned.getStatus() != MailEnums.DomainStatus.VERIFIED) {
            throw ApiException.invalidState(domain + " has not finished verifying yet");
        }

        if (aliasRepository.findByAddressIgnoreCase(address).isPresent()) {
            throw ApiException.conflict("That address is already in use");
        }
        if (aliasRepository.findByMailboxIdAndOrganizationId(mailboxId, orgId).size() >= MAX_PER_MAILBOX) {
            throw ApiException.of(com.prabhix.platform.common.error.ErrorCode.LIMIT_EXCEEDED,
                    "This mailbox already has the maximum number of addresses");
        }

        MailAlias alias = new MailAlias();
        alias.setOrganizationId(orgId);
        alias.setMailboxId(mailboxId);
        alias.setAddress(address);
        alias = aliasRepository.save(alias);
        return new MailboxDtos.AliasView(alias.getId(), mailboxId, alias.getAddress(),
                alias.getCreatedAt());
    }

    @Transactional
    public void delete(PrabhixPrincipal principal, UUID mailboxId, UUID aliasId) {
        access.requireMailbox(principal, mailboxId);
        MailAlias alias = aliasRepository.findById(aliasId)
                .filter(a -> a.getOrganizationId().equals(principal.requireOrganizationId()))
                .filter(a -> a.getMailboxId().equals(mailboxId))
                .orElseThrow(() -> ApiException.notFound("Address"));
        aliasRepository.delete(alias);
    }
}
