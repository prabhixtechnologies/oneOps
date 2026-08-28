package com.prabhix.platform.visitor.service;

import com.prabhix.platform.common.util.Ids;
import com.prabhix.platform.visitor.domain.Visitor;
import com.prabhix.platform.visitor.domain.VisitorEnums;
import com.prabhix.platform.visitor.repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitorStitchService {

    private final VisitorRepository visitorRepository;

    /**
     * Attaches an identity to the browsing history behind {@code externalKey}.
     *
     * <p>When the email already belongs to another visitor row — a returning customer on a
     * new device, say — this visit's sessions, page views, and events move onto that row and
     * the one for this device becomes a tombstone. The <em>surviving</em> visitor is what
     * comes back, because callers store the returned id: handing back the tombstone would
     * point a conversation at a row whose history had just been moved out from under it.
     */
    @Transactional
    public Visitor identify(UUID organizationId, String externalKey, String email,
                            String displayName, UUID userId) {
        Visitor visitor = visitorRepository
                .findByOrganizationIdAndExternalKeyAndDeletedAtIsNull(organizationId, externalKey)
                .orElseGet(() -> createVisitor(organizationId, externalKey));

        String normalizedEmail = email == null || email.isBlank() ? null : email.toLowerCase();

        if (normalizedEmail != null) {
            UUID thisDeviceId = visitor.getId();
            UUID survivingId = visitorRepository
                    .findFirstByOrganizationIdAndEmailIgnoreCaseAndDeletedAtIsNullAndMergedIntoIdIsNull(
                            organizationId, normalizedEmail)
                    .map(Visitor::getId)
                    .filter(id -> !id.equals(thisDeviceId))
                    .orElse(null);

            if (survivingId != null) {
                mergeVisitors(organizationId, thisDeviceId, survivingId);
                visitor = visitorRepository.findById(survivingId).orElseThrow();
            }

            visitor.setEmail(normalizedEmail);
            if (displayName != null && !displayName.isBlank()) {
                visitor.setDisplayName(displayName);
            }
            markIdentified(visitor);
        }
        if (userId != null) {
            visitor.setIdentifiedUserId(userId);
            markIdentified(visitor);
        }
        visitor.setLastSeenAt(Instant.now());
        return visitorRepository.save(visitor);
    }

    private void markIdentified(Visitor visitor) {
        if (visitor.getIdentifiedAt() == null) {
            visitor.setIdentifiedAt(Instant.now());
        }
    }

    @Transactional
    public void mergeVisitors(UUID organizationId, UUID sourceId, UUID targetId) {
        if (sourceId.equals(targetId)) {
            return;
        }
        visitorRepository.reassignSessions(organizationId, sourceId, targetId);
        visitorRepository.reassignPageViews(organizationId, sourceId, targetId);
        visitorRepository.reassignEvents(organizationId, sourceId, targetId);
        visitorRepository.mergeVisitor(organizationId, sourceId, targetId);
    }

    private Visitor createVisitor(UUID organizationId, String externalKey) {
        Visitor visitor = new Visitor();
        visitor.setOrganizationId(organizationId);
        visitor.setExternalKey(externalKey);
        visitor.setConsentStatus(VisitorEnums.ConsentStatus.FULL);
        visitor.setFirstSeenAt(Instant.now());
        visitor.setLastSeenAt(Instant.now());
        visitor.setFirstTouchUtm(Map.of());
        return visitorRepository.save(visitor);
    }

    public String issueExternalKey() {
        return Ids.token(24);
    }
}
