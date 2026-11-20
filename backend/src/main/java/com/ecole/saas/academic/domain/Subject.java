package com.ecole.saas.academic.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "subjects")
public class Subject extends TenantScopedEntity {

    public static final int DEFAULT_COEFFICIENT = 1;
    public static final int MAX_COEFFICIENT = 50;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "short_name", length = 30)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SubjectCategory category;

    @Column(name = "default_coefficient", nullable = false)
    private int defaultCoefficient;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected Subject() {
    }

    public Subject(
            String code,
            String name,
            String shortName,
            SubjectCategory category,
            int defaultCoefficient) {
        this.code = normalizeCode(code);
        this.name = requireName(name);
        this.shortName = normalizeShortName(shortName);
        this.category = category != null ? category : SubjectCategory.GENERAL;
        this.defaultCoefficient = validateCoefficient(defaultCoefficient);
        this.active = true;
    }

    public void update(String newName, String newShortName, SubjectCategory newCategory, int newDefaultCoefficient) {
        this.name = requireName(newName);
        this.shortName = normalizeShortName(newShortName);
        this.category = newCategory != null ? newCategory : SubjectCategory.GENERAL;
        this.defaultCoefficient = validateCoefficient(newDefaultCoefficient);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
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

    public String getShortName() {
        return shortName;
    }

    public SubjectCategory getCategory() {
        return category;
    }

    public int getDefaultCoefficient() {
        return defaultCoefficient;
    }

    public boolean isActive() {
        return active;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de la matière est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de la matière doit contenir entre 1 et 20 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    public static int validateCoefficient(int coefficient) {
        if (coefficient <= 0 || coefficient > MAX_COEFFICIENT) {
            throw new IllegalArgumentException(
                    "Le coefficient doit être compris entre 1 et " + MAX_COEFFICIENT + ".");
        }
        return coefficient;
    }

    private static String requireName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la matière est obligatoire.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("Le nom de la matière ne peut pas dépasser 120 caractères.");
        }
        return trimmed;
    }

    private static String normalizeShortName(String rawShortName) {
        if (rawShortName == null || rawShortName.isBlank()) {
            return null;
        }
        String trimmed = rawShortName.trim();
        if (trimmed.length() > 30) {
            throw new IllegalArgumentException("L'abréviation de la matière ne peut pas dépasser 30 caractères.");
        }
        return trimmed;
    }
}
