package com.ecole.saas.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnEmptyWhenNoTenantIsSet() {
        assertThat(TenantContext.getCurrent()).isEmpty();
        assertThatThrownBy(TenantContext::requireCurrent)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRestorePreviousTenantAfterExecuteWithTenant() {
        TenantId tenantA = TenantId.of(UUID.randomUUID());
        TenantId tenantB = TenantId.of(UUID.randomUUID());

        TenantContext.set(tenantA);
        TenantId inside = TenantContext.executeWithTenant(tenantB, TenantContext::requireCurrent);

        assertThat(inside).isEqualTo(tenantB);
        assertThat(TenantContext.requireCurrent()).isEqualTo(tenantA);
    }

    @Test
    void shouldRestorePreviousTenantEvenWhenActionThrows() {
        TenantId tenantA = TenantId.of(UUID.randomUUID());
        TenantId tenantB = TenantId.of(UUID.randomUUID());

        TenantContext.set(tenantA);
        assertThatThrownBy(() -> TenantContext.runWithTenant(tenantB, () -> {
            assertThat(TenantContext.requireCurrent()).isEqualTo(tenantB);
            throw new IllegalStateException("Erreur simulée");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(TenantContext.requireCurrent()).isEqualTo(tenantA);
    }
}
