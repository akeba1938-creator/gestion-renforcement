package com.ecole.saas.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "monthly_price_xof", nullable = false)
    private long monthlyPriceXof;

    @Column(name = "yearly_price_xof", nullable = false)
    private long yearlyPriceXof;

    @Column(name = "max_students", nullable = false)
    private int maxStudents;

    @Column(name = "max_users", nullable = false)
    private int maxUsers;

    @Column(name = "max_storage_mb", nullable = false)
    private int maxStorageMb;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SubscriptionPlan() {
    }

    public SubscriptionPlan(
            String code,
            String name,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code du forfait est obligatoire.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du forfait est obligatoire.");
        }
        if (monthlyPriceXof < 0 || yearlyPriceXof < 0) {
            throw new IllegalArgumentException("Les tarifs en FCFA doivent être positifs ou nuls.");
        }
        if (maxStudents <= 0 || maxUsers <= 0 || maxStorageMb <= 0) {
            throw new IllegalArgumentException("Les quotas du forfait doivent être strictement positifs.");
        }
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.monthlyPriceXof = monthlyPriceXof;
        this.yearlyPriceXof = yearlyPriceXof;
        this.maxStudents = maxStudents;
        this.maxUsers = maxUsers;
        this.maxStorageMb = maxStorageMb;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getMonthlyPriceXof() {
        return monthlyPriceXof;
    }

    public long getYearlyPriceXof() {
        return yearlyPriceXof;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public int getMaxStorageMb() {
        return maxStorageMb;
    }

    public boolean isActive() {
        return active;
    }
}
