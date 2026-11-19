package com.ecole.saas.shared.infrastructure.tenant;

import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID NO_TENANT_SENTINEL = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrent()
                .map(TenantId::value)
                .orElse(NO_TENANT_SENTINEL);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
