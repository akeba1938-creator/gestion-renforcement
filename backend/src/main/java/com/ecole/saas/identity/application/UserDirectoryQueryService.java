package com.ecole.saas.identity.application;

import com.ecole.saas.identity.UserContactSummary;
import com.ecole.saas.identity.UserDirectoryPort;
import com.ecole.saas.identity.UserSummary;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.domain.UserRole;
import com.ecole.saas.identity.domain.UserStatus;
import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.identity.infrastructure.UserRoleRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final Set<String> NON_STAFF_ROLES = Set.of(RoleCode.PARENT.name(), RoleCode.STUDENT.name());

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

    @Override
    @Transactional(readOnly = true)
    public Optional<UserContactSummary> findUserContactById(UUID userId) {
        TenantContext.requireCurrent();
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::toContactSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContactSummary> findAllActiveStaffUsers() {
        TenantContext.requireCurrent();
        List<User> activeUsers = userRepository.findByStatusOrderByLastNameAscFirstNameAsc(UserStatus.ACTIVE);
        if (activeUsers.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = activeUsers.stream().map(User::getId).toList();
        Map<UUID, Set<String>> rolesByUserId = new HashMap<>();
        for (UserRole userRole : userRoleRepository.findByUserIdIn(userIds)) {
            rolesByUserId
                    .computeIfAbsent(userRole.getUserId(), ignored -> new HashSet<>())
                    .add(userRole.getRoleCode().name());
        }
        return activeUsers.stream()
                .map(user -> toContactSummary(
                        user,
                        Set.copyOf(rolesByUserId.getOrDefault(user.getId(), Set.of()))))
                .filter(summary -> summary.roles().stream().anyMatch(r -> !NON_STAFF_ROLES.contains(r)))
                .toList();
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

    private UserContactSummary toContactSummary(User user) {
        Set<String> roles = userRoleRepository.findByUserId(user.getId()).stream()
                .map(role -> role.getRoleCode().name())
                .collect(Collectors.toUnmodifiableSet());
        return toContactSummary(user, roles);
    }

    private static UserContactSummary toContactSummary(User user, Set<String> roles) {
        return new UserContactSummary(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.isActive(),
                roles);
    }
}
