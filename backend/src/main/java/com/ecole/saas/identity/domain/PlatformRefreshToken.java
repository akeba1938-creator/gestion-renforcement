package com.ecole.saas.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "platform_refresh_tokens")
public class PlatformRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private UUID adminId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformRefreshToken() {
    }

    public PlatformRefreshToken(UUID adminId, String tokenHash, UUID familyId, Instant expiresAt) {
        this.adminId = Objects.requireNonNull(adminId, "L'administrateur est obligatoire.");
        this.tokenHash = Objects.requireNonNull(tokenHash, "L'empreinte du jeton est obligatoire.");
        this.familyId = Objects.requireNonNull(familyId, "La famille du jeton est obligatoire.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "L'expiration est obligatoire.");
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void replaceBy(UUID nextTokenId, Instant now) {
        revoke(now);
        this.replacedBy = nextTokenId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public UUID getFamilyId() {
        return familyId;
    }
}
