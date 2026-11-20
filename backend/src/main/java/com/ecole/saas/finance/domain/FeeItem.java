package com.ecole.saas.finance.domain;

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

/**
 * Élément du catalogue des frais d'un établissement pour une année scolaire (montants en FCFA entiers).
 */
@Entity
@Table(name = "fee_items")
public class FeeItem extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "grade_level_id")
    private UUID gradeLevelId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private FeeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private FeeFrequency frequency;

    @Column(name = "amount_xof", nullable = false)
    private long amountXof;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "description")
    private String description;

    protected FeeItem() {
    }

    public FeeItem(
            UUID academicYearId,
            UUID gradeLevelId,
            String code,
            String label,
            FeeCategory category,
            FeeFrequency frequency,
            long amountXof,
            boolean mandatory,
            String description) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.gradeLevelId = gradeLevelId;
        this.code = FinanceCodeFormatter.normalizeFeeCode(code);
        this.label = normalizeRequiredText(label, "Le libellé du frais est obligatoire.", 150);
        this.category = Objects.requireNonNull(category, "La catégorie du frais est obligatoire.");
        this.frequency = Objects.requireNonNull(frequency, "La périodicité du frais est obligatoire.");
        this.amountXof = MoneyMath.requireNonNegative(amountXof, "Le montant du frais");
        this.mandatory = mandatory;
        this.active = true;
        this.description = normalizeOptionalText(description);
    }

    public void update(
            UUID gradeLevelId,
            String label,
            FeeCategory category,
            FeeFrequency frequency,
            long amountXof,
            boolean mandatory,
            String description) {
        if (!this.active) {
            throw new FinanceConflictException(
                    "fee-item-inactive",
                    "Frais inactif",
                    "Un frais désactivé ne peut pas être modifié avant réactivation.");
        }
        this.gradeLevelId = gradeLevelId;
        this.label = normalizeRequiredText(label, "Le libellé du frais est obligatoire.", 150);
        this.category = Objects.requireNonNull(category, "La catégorie du frais est obligatoire.");
        this.frequency = Objects.requireNonNull(frequency, "La périodicité du frais est obligatoire.");
        this.amountXof = MoneyMath.requireNonNegative(amountXof, "Le montant du frais");
        this.mandatory = mandatory;
        this.description = normalizeOptionalText(description);
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

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getGradeLevelId() {
        return gradeLevelId;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public FeeCategory getCategory() {
        return category;
    }

    public FeeFrequency getFrequency() {
        return frequency;
    }

    public long getAmountXof() {
        return amountXof;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public boolean isActive() {
        return active;
    }

    public String getDescription() {
        return description;
    }

    private static String normalizeRequiredText(String raw, String message, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Le texte dépasse la longueur maximale autorisée (" + maxLength + ").");
        }
        return trimmed;
    }

    private static String normalizeOptionalText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }
}
