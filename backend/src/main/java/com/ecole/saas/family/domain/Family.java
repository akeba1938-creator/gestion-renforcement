package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "families")
public class Family extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "primary_phone", length = 20)
    private String primaryPhone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FamilyStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Family() {
    }

    public Family(
            String code,
            String name,
            String primaryPhone,
            String email,
            String address,
            String city,
            String notes) {
        this.code = normalizeOptionalCode(code);
        this.name = requireNonBlank(name, 150, "Le nom de la famille est obligatoire.");
        this.primaryPhone = normalizePhone(primaryPhone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.city = normalizeOptional(city, 100, "La ville");
        this.notes = normalizeOptional(notes, 1000, "Les observations");
        this.status = FamilyStatus.ACTIVE;
    }

    public void update(
            String code,
            String name,
            String primaryPhone,
            String email,
            String address,
            String city,
            String notes) {
        assertActive();
        this.code = normalizeOptionalCode(code);
        this.name = requireNonBlank(name, 150, "Le nom de la famille est obligatoire.");
        this.primaryPhone = normalizePhone(primaryPhone);
        this.email = normalizeEmail(email);
        this.address = normalizeOptional(address, 500, "L'adresse");
        this.city = normalizeOptional(city, 100, "La ville");
        this.notes = normalizeOptional(notes, 1000, "Les observations");
    }

    public void archive(Instant at) {
        this.archivedAt = Objects.requireNonNull(at, "La date d'archivage est obligatoire.");
        this.status = FamilyStatus.ARCHIVED;
    }

    public void reactivate() {
        this.status = FamilyStatus.ACTIVE;
        this.archivedAt = null;
    }

    public boolean isActive() {
        return this.status == FamilyStatus.ACTIVE;
    }

    public void assertActive() {
        if (this.status != FamilyStatus.ACTIVE) {
            throw new FamilyArchivedException("Une famille archivée ne peut plus être modifiée.");
        }
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

    public String getPrimaryPhone() {
        return primaryPhone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getNotes() {
        return notes;
    }

    public FamilyStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public static String normalizeOptionalCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code famille doit contenir entre 2 et 32 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    private static String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        String normalized = rawPhone.trim();
        if (normalized.length() > 20 || !E164_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le numéro de téléphone doit respecter le format international E.164 (ex. +221770000001).");
        }
        return normalized;
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return null;
        }
        String normalized = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("L'adresse e-mail de la famille est invalide.");
        }
        return normalized;
    }

    private static String requireNonBlank(String raw, int maxLength, String blankMessage) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    "La valeur dépasse la longueur maximale autorisée de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldLabel + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
