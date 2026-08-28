package com.prabhix.platform.security.tenant;

import com.prabhix.platform.common.entity.TenantScopedEntity;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantFilterEnablerTest {

    @Mock
    Session session;

    @Mock
    Filter filter;

    private final UUID orgA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID orgB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void enablesFilterWhenTenantIsSet() {
        TenantContext.set(orgA);
        when(session.getEnabledFilter(TenantScopedEntity.FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(TenantScopedEntity.FILTER_NAME)).thenReturn(filter);

        TenantFilterEnabler.apply(session);

        verify(session).enableFilter(TenantScopedEntity.FILTER_NAME);
        verify(filter).setParameter(TenantScopedEntity.FILTER_PARAM, orgA);
    }

    @Test
    void skipsFilterWhenNoTenantIsSet() {
        TenantFilterEnabler.apply(session);
        verify(session, never()).enableFilter(TenantScopedEntity.FILTER_NAME);
    }

    @Test
    void updatesParameterWhenFilterAlreadyEnabled() {
        TenantContext.set(orgB);
        when(session.getEnabledFilter(TenantScopedEntity.FILTER_NAME)).thenReturn(filter);

        TenantFilterEnabler.apply(session);

        verify(filter).setParameter(TenantScopedEntity.FILTER_PARAM, orgB);
        verify(session, never()).enableFilter(TenantScopedEntity.FILTER_NAME);
    }

    @Test
    void runAsAppliesBridgeHook() {
        TenantFilterBridge.setApplier(() -> {
            if (TenantContext.isSet()) {
                TenantFilterEnabler.apply(session);
            }
        });
        when(session.getEnabledFilter(TenantScopedEntity.FILTER_NAME)).thenReturn(null);
        when(session.enableFilter(TenantScopedEntity.FILTER_NAME)).thenReturn(filter);

        TenantContext.runAs(orgA, () ->
                assertTrue(TenantContext.current().isPresent()));

        verify(session).enableFilter(TenantScopedEntity.FILTER_NAME);
        TenantFilterBridge.setApplier(() -> {});
    }
}
