package com.ecole.saas.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 30)
    private RoleCode code;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "platform_role", nullable = false)
    private boolean platformRole;

    protected Role() {
    }

    public RoleCode getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPlatformRole() {
        return platformRole;
    }
}
