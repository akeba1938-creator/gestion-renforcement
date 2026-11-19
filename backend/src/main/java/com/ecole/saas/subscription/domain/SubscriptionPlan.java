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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlan {

    public static final int DEFAULT_TRIAL_DAYS = 14;
    public static final int MAX_TRIAL_DAYS = 365;
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,39}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

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

    @Column(name = "trial_days", nullable = false)
    private int trialDays;

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
        this(
                code,
                name,
                null,
                monthlyPriceXof,
                yearlyPriceXof,
                maxStudents,
                maxUsers,
                maxStorageMb,
                DEFAULT_TRIAL_DAYS);
    }

    public SubscriptionPlan(
            String code,
            String name,
            String description,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) {
        this.code = normalizeCode(code);
        applyDetails(
                name,
                description,
                monthlyPriceXof,
                yearlyPriceXof,
                maxStudents,
                maxUsers,
                maxStorageMb,
                trialDays);
        this.active = true;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code du forfait est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code du forfait doit contenir uniquement des lettres majuscules, chiffres, tirets ou underscores (40 caractères max).");
        }
        return normalized;
    }

    public void updateDetails(
            String name,
            String description,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) {
        applyDetails(
                name,
                description,
                monthlyPriceXof,
                yearlyPriceXof,
                maxStudents,
                maxUsers,
                maxStorageMb,
                trialDays);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public long priceFor(BillingCycle billingCycle) {
        Objects.requireNonNull(billingCycle, "Le cycle de facturation est obligatoire.");
        return billingCycle == BillingCycle.YEARLY ? yearlyPriceXof : monthlyPriceXof;
    }

    private void applyDetails(
            String name,
            String description,
            long monthlyPriceXof,
            long yearlyPriceXof,
            int maxStudents,
            int maxUsers,
            int maxStorageMb,
            int trialDays) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du forfait est obligatoire.");
        }
        String trimmedName = name.trim();
        if (trimmedName.length() > 100) {
            throw new IllegalArgumentException("Le nom du forfait ne peut pas dépasser 100 caractères.");
        }
        if (monthlyPriceXof < 0 || yearlyPriceXof < 0) {
            throw new IllegalArgumentException("Les tarifs en FCFA doivent être positifs ou nuls.");
        }
        if (maxStudents <= 0 || maxUsers <= 0 || maxStorageMb <= 0) {
            throw new IllegalArgumentException("Les quotas du forfait doivent être strictement positifs.");
        }
        if (trialDays < 0 || trialDays > MAX_TRIAL_DAYS) {
            throw new IllegalArgumentException(
                    "La durée d'essai doit être comprise entre 0 et " + MAX_TRIAL_DAYS + " jours.");
        }
        this.name = trimmedName;
        this.description = (description == null || description.isBlank()) ? null : description.trim();
        this.monthlyPriceXof = monthlyPriceXof;
        this.yearlyPriceXof = yearlyPriceXof;
        this.maxStudents = maxStudents;
        this.maxUsers = maxUsers;
        this.maxStorageMb = maxStorageMb;
        this.trialDays = trialDays;
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

    public String getDescription() {
        return description;
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

    public int getTrialDays() {
        return trialDays;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
