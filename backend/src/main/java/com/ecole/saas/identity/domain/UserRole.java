package com.ecole.saas.identity.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_roles")
public class UserRole extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 30)
    private RoleCode roleCode;

    protected UserRole() {
    }

    public UserRole(UUID userId, RoleCode roleCode) {
        this.userId = Objects.requireNonNull(userId, "L'identifiant utilisateur est obligatoire.");
        this.roleCode = Objects.requireNonNull(roleCode, "Le rôle est obligatoire.");
        if (roleCode == RoleCode.ADMIN_SAAS) {
            throw new IllegalArgumentException("Le rôle ADMIN_SAAS est réservé aux administrateurs plateforme.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public RoleCode getRoleCode() {
        return roleCode;
    }
}
