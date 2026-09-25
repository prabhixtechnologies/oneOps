package com.prabhix.platform.security.tenant;

import com.prabhix.platform.common.error.ApiException;
import com.prabhix.platform.common.error.ErrorCode;
import com.prabhix.platform.security.PrabhixPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantPathGuardTest {

    private final TenantPathGuard guard = new TenantPathGuard();
    private final UUID ownOrg = UUID.randomUUID();
    private final UUID foreignOrg = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UUID organizationId, boolean platformAdmin) {
        PrabhixPrincipal principal = new PrabhixPrincipal(
                UUID.randomUUID(), "user@example.com", "User",
                organizationId, Set.of(), UUID.randomUUID(), platformAdmin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private MockHttpServletRequest request(String route, Map<String, String> query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", route);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, route);
        query.forEach(request::setParameter);
        return request;
    }

    private boolean preHandle(MockHttpServletRequest request) {
        return guard.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void allowsOwnOrganization() {
        authenticate(ownOrg, false);
        var request = request("/api/v1/oneops/organizations/members",
                Map.of("orgId", ownOrg.toString()));
        assertTrue(assertDoesNotThrow(() -> preHandle(request)));
    }

    @Test
    void rejectsForeignOrganizationInOrgIdVariable() {
        authenticate(ownOrg, false);
        var request = request("/api/v1/oneops/organizations/members",
                Map.of("orgId", foreignOrg.toString()));
        ApiException thrown = assertThrows(ApiException.class, () -> preHandle(request));
        assertEquals(ErrorCode.CROSS_TENANT_ACCESS, thrown.getCode());
    }

    @Test
    void rejectsForeignOrganizationOnOrganizationsIdRoute() {
        authenticate(ownOrg, false);
        var request = request("/api/v1/oneops/organizations",
                Map.of("id", foreignOrg.toString()));
        assertThrows(ApiException.class, () -> preHandle(request));
    }

    @Test
    void allowsForeignOrganizationOnSelectRoute() {
        authenticate(ownOrg, false);
        var request = request("/api/v1/oneops/organizations/select",
                Map.of("id", foreignOrg.toString()));
        assertTrue(assertDoesNotThrow(() -> preHandle(request)));
    }

    @Test
    void ignoresUnrelatedIdVariables() {
        authenticate(ownOrg, false);
        var request = request("/api/v1/oneops/mail/threads",
                Map.of("id", UUID.randomUUID().toString()));
        assertTrue(assertDoesNotThrow(() -> preHandle(request)));
    }

    @Test
    void allowsPlatformAdminCrossTenant() {
        authenticate(ownOrg, true);
        var request = request("/api/v1/oneops/organizations",
                Map.of("id", foreignOrg.toString()));
        assertTrue(assertDoesNotThrow(() -> preHandle(request)));
    }

    @Test
    void ignoresAnonymousRequests() {
        var request = request("/api/v1/oneops/organizations/members",
                Map.of("orgId", foreignOrg.toString()));
        assertTrue(assertDoesNotThrow(() -> preHandle(request)));
    }
}
