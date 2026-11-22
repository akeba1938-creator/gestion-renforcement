package com.ecole.saas.identity.application;

import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.QuotaUsageProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remonte le nombre d'utilisateurs du tenant courant au module {@code subscription}
 * via le SPI {@link QuotaUsageProvider} ({@link QuotaResource#USERS}).
 */
@Component
public class UserQuotaUsageProvider implements QuotaUsageProvider {

    private final UserRepository userRepository;

    public UserQuotaUsageProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public QuotaResource resource() {
        return QuotaResource.USERS;
    }

    @Override
    @Transactional(readOnly = true)
    public long currentUsageForCurrentTenant() {
        TenantContext.requireCurrent();
        return userRepository.count();
    }
}
