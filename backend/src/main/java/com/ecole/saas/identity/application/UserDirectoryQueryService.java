package com.ecole.saas.identity.application;

import com.ecole.saas.identity.UserDirectoryPort;
import com.ecole.saas.identity.UserSummary;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.identity.infrastructure.UserRoleRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation en lecture seule de {@link UserDirectoryPort} dans le contexte tenant courant.
 */
@Service
public class UserDirectoryQueryService implements UserDirectoryPort {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserDirectoryQueryService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findUserById(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::toSummary);
    }

    private UserSummary toSummary(User user) {
        Set<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(role -> role.getRoleCode().name())
                .collect(Collectors.toUnmodifiableSet());
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus().name(),
                user.isActive(),
                roles);
    }
}
