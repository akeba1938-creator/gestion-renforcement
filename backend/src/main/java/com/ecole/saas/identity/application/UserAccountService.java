package com.ecole.saas.identity.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.identity.domain.RoleCode;
import com.ecole.saas.identity.domain.User;
import com.ecole.saas.identity.domain.UserRole;
import com.ecole.saas.identity.infrastructure.UserRepository;
import com.ecole.saas.identity.infrastructure.UserRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogPort auditLogPort;

    public UserAccountService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            AuditLogPort auditLogPort) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogPort = auditLogPort;
    }

    @Transactional
    public User createUserWithRole(
            String email,
            String phone,
            String firstName,
            String lastName,
            String passwordHash,
            RoleCode roleCode) {
        User saved = userRepository.save(new User(email, phone, firstName, lastName, passwordHash));
        userRoleRepository.save(new UserRole(saved.getId(), roleCode));
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.SECURITY,
                AuditAction.USER_CREATED,
                "USER",
                saved.getId(),
                "Création d'un compte utilisateur d'établissement.",
                Map.of("roleCode", roleCode.name())));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<User> findAllForCurrentTenant() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<UserRole> findRolesByUserId(UUID userId) {
        return userRoleRepository.findByUserId(userId);
    }
}
