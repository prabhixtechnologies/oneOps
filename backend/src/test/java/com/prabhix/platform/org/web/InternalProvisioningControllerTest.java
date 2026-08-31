package com.prabhix.platform.org.web;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.org.domain.OrganizationMembership;
import com.prabhix.platform.org.domain.OrganizationMembership.MembershipStatus;
import com.prabhix.platform.org.dto.OrgDtos.OrganizationView;
import com.prabhix.platform.org.repository.OrganizationMembershipRepository;
import com.prabhix.platform.org.service.OrganizationService;
import com.prabhix.platform.org.web.InternalProvisioningController.ProvisionRequest;
import com.prabhix.platform.support.TestProperties;
import com.prabhix.platform.user.service.IdentityUserMirror;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The endpoint that creates an organization and hands somebody its ownership.
 *
 * <p>Two things are worth pinning here and they are both about what it refuses. The token gate,
 * because this is the most valuable thing either service exposes and it is reachable by anything that
 * can address the container network. And the repeat, because the caller is a network hop away: a
 * timeout it retries after may have succeeded, and a second organization nobody asked for is not a
 * harmless duplicate — it competes with the first to be the one the person lands in.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalProvisioningControllerTest {

    @Mock private OrganizationService organizations;
    @Mock private OrganizationMembershipRepository memberships;
    @Mock private IdentityUserMirror mirror;

    private InternalProvisioningController controller;

    private static final String TOKEN = "a-shared-service-token";
    private final UUID userId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new InternalProvisioningController(
                propertiesWithToken(TOKEN), organizations, memberships, mirror);

        when(memberships.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
        when(organizations.create(any(), any())).thenReturn(view(orgId));
    }

    @Test
    void createsTheWorkspaceAndTheMirrorRowItNeeds() {
        var response = controller.provision(TOKEN, request("Acme"));

        assertThat(response.organizationId()).isEqualTo(orgId);
        assertThat(response.created()).isTrue();
        // The mirror row first: the membership about to be written has a foreign key to it.
        verify(mirror).mirrorFromSignup(userId, "someone@example.com", false, "Someone");
        verify(organizations).create(userId, new com.prabhix.platform.org.dto.OrgDtos
                .CreateOrganizationRequest("Acme"));
    }

    @Test
    void aRepeatedCallReturnsTheWorkspaceTheyAlreadyHave() {
        UUID existing = UUID.randomUUID();
        when(memberships.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(membership(existing)));

        var response = controller.provision(TOKEN, request("Acme"));

        assertThat(response.organizationId()).isEqualTo(existing);
        assertThat(response.created()).isFalse();
        // Not a second organization. Two would compete to be the one the default-organization column
        // names, and whichever lost would be invisible to the person who owns it.
        verify(organizations, never()).create(any(), any());
    }

    @Test
    void refusesAWrongToken() {
        assertThatThrownBy(() -> controller.provision("not-the-token", request("Acme")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(mirror, never()).mirrorFromSignup(any(), any(), anyBoolean(), any());
        verify(organizations, never()).create(any(), any());
    }

    @Test
    void refusesAMissingToken() {
        assertThatThrownBy(() -> controller.provision(null, request("Acme")))
                .isInstanceOf(ApiException.class);
        verify(organizations, never()).create(any(), any());
    }

    @Test
    void refusesEverythingWhenNoTokenIsConfigured() {
        // Fails closed. A deployment that forgot to set one must not accept a blank header as a match,
        // which is what comparing two empty strings would do.
        var unconfigured = new InternalProvisioningController(
                propertiesWithToken(""), organizations, memberships, mirror);

        assertThatThrownBy(() -> unconfigured.provision("", request("Acme")))
                .isInstanceOf(ApiException.class);
        verify(organizations, never()).create(any(), any());
    }

    // ------------------------------------------------------------------

    private ProvisionRequest request(String organizationName) {
        return new ProvisionRequest(userId, "someone@example.com", false, "Someone", organizationName);
    }

    private OrganizationMembership membership(UUID organizationId) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setId(UUID.randomUUID());
        membership.setOrganizationId(organizationId);
        membership.setUserId(userId);
        membership.setStatus(MembershipStatus.ACTIVE);
        return membership;
    }

    private OrganizationView view(UUID id) {
        return new OrganizationView(id, "Acme", "acme", "TRIAL", 1, 5,
                Instant.now(), "Asia/Kolkata", "en-IN", "INR", Instant.now());
    }

    private PrabhixProperties propertiesWithToken(String token) {
        return TestProperties.withSecurity(TestProperties.security(
                Duration.ofMinutes(15), Duration.ofDays(30),
                TestProperties.identityWithServiceToken(token)));
    }
}
