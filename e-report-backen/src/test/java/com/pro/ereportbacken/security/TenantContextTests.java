package com.pro.ereportbacken.security;

import com.pro.ereportbacken.service.DefaultTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TenantContextTests {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void backgroundExecutionUsesAndThenClearsExplicitTenant() {
        TenantContext tenantContext = new TenantContext(
                mock(DefaultTenantService.class),
                new TenantExecutionContext()
        );
        UUID tenantId = com.pro.ereportbacken.TestIds.uuid(42);
        AtomicReference<UUID> observedTenant = new AtomicReference<>();

        tenantContext.runInTenant(tenantId, () -> observedTenant.set(tenantContext.requireTenantId()));

        assertThat(observedTenant).hasValue(tenantId);
        assertThatThrownBy(tenantContext::requireTenantId).isInstanceOf(AccessDeniedException.class);
    }
}
