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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "grade_levels")
public class GradeLevel extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle", nullable = false, length = 30)
    private EducationCycle cycle;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected GradeLevel() {
    }

    public GradeLevel(String code, String name, EducationCycle cycle, int displayOrder) {
        this.code = normalizeCode(code);
        this.name = requireName(name);
        this.cycle = Objects.requireNonNull(cycle, "Le cycle pédagogique est obligatoire.");
        this.displayOrder = validateDisplayOrder(displayOrder);
        this.active = true;
    }

    public void update(String newName, EducationCycle newCycle, int newDisplayOrder) {
        this.name = requireName(newName);
        this.cycle = Objects.requireNonNull(newCycle, "Le cycle pédagogique est obligatoire.");
        this.displayOrder = validateDisplayOrder(newDisplayOrder);
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

    public EducationCycle getCycle() {
        return cycle;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code du niveau est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code du niveau doit contenir entre 1 et 20 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    private static String requireName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Le nom du niveau est obligatoire.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Le nom du niveau ne peut pas dépasser 100 caractères.");
        }
        return trimmed;
    }

    private static int validateDisplayOrder(int displayOrder) {
        if (displayOrder <= 0) {
            throw new IllegalArgumentException("L'ordre d'affichage du niveau doit être strictement positif.");
        }
        return displayOrder;
    }
}
