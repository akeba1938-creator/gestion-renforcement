package com.ecole.saas.center.domain;

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

/**
 * Catalogue de forfaits et packs de séances en FCFA entiers pour le mode centre de renforcement.
 */
@Entity
@Table(name = "center_session_packs")
public class CenterSessionPack extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,29}$");
    public static final int MAX_SESSIONS = 500;
    public static final int MAX_DURATION_DAYS = 365;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id")
    private UUID academicYearId;

    @Column(name = "grade_level_id")
    private UUID gradeLevelId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private CenterPackBillingMode billingMode;

    @Column(name = "included_sessions")
    private Integer includedSessions;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "price_xof", nullable = false)
    private long priceXof;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected CenterSessionPack() {
    }

    public CenterSessionPack(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            String code,
            String name,
            String description,
            CenterPackBillingMode billingMode,
            Integer includedSessions,
            Integer durationDays,
            long priceXof) {
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.subjectId = subjectId;
        this.code = normalizeCode(code);
        this.name = requireText(name, "Le libellé du forfait est obligatoire.", 120);
        this.description = normalizeOptional(description, 500);
        this.billingMode = requireBillingMode(billingMode);
        this.includedSessions = validateIncludedSessions(this.billingMode, includedSessions);
        this.durationDays = validateDurationDays(durationDays);
        this.priceXof = validatePriceXof(priceXof);
        this.active = true;
    }

    public void updateDetails(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            String name,
            String description,
            CenterPackBillingMode billingMode,
            Integer includedSessions,
            Integer durationDays,
            long priceXof) {
        this.academicYearId = academicYearId;
        this.gradeLevelId = gradeLevelId;
        this.subjectId = subjectId;
        this.name = requireText(name, "Le libellé du forfait est obligatoire.", 120);
        this.description = normalizeOptional(description, 500);
        this.billingMode = requireBillingMode(billingMode);
        this.includedSessions = validateIncludedSessions(this.billingMode, includedSessions);
        this.durationDays = validateDurationDays(durationDays);
        this.priceXof = validatePriceXof(priceXof);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new CenterValidationException(
                    "center-pack-invalid", "Forfait invalide", "Le code du forfait est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Code forfait invalide",
                    "Le code du forfait doit contenir 2 à 30 caractères alphanumériques majuscules ('-' et '_' autorisés).");
        }
        return normalized;
    }

    private static CenterPackBillingMode requireBillingMode(CenterPackBillingMode mode) {
        if (mode == null) {
            throw new CenterValidationException(
                    "center-pack-invalid", "Mode de facturation obligatoire", "Le mode de facturation du forfait est obligatoire.");
        }
        return mode;
    }

    private static Integer validateIncludedSessions(CenterPackBillingMode mode, Integer sessions) {
        if (mode == CenterPackBillingMode.SESSION_PACK) {
            if (sessions == null || sessions < 1 || sessions > MAX_SESSIONS) {
                throw new CenterValidationException(
                        "center-pack-invalid",
                        "Nombre de séances invalide",
                        "Un pack de séances doit inclure entre 1 et " + MAX_SESSIONS + " séances.");
            }
            return sessions;
        }
        if (sessions != null && (sessions < 1 || sessions > MAX_SESSIONS)) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Plafond de séances invalide",
                    "Le plafond optionnel de séances doit être compris entre 1 et " + MAX_SESSIONS + ".");
        }
        return sessions;
    }

    private static Integer validateDurationDays(Integer days) {
        if (days != null && (days < 1 || days > MAX_DURATION_DAYS)) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Durée de validité invalide",
                    "La durée de validité d'un forfait doit être comprise entre 1 et " + MAX_DURATION_DAYS + " jours.");
        }
        return days;
    }

    private static long validatePriceXof(long amount) {
        if (amount < 0L) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Montant FCFA invalide",
                    "Le prix du forfait en FCFA doit être un entier positif ou nul.");
        }
        return amount;
    }

    private static String requireText(String raw, String message, int maxLen) {
        if (raw == null || raw.isBlank()) {
            throw new CenterValidationException("center-pack-invalid", "Forfait invalide", message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Forfait invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-pack-invalid",
                    "Forfait invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
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

    public UUID getSubjectId() {
        return subjectId;
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

    public CenterPackBillingMode getBillingMode() {
        return billingMode;
    }

    public Integer getIncludedSessions() {
        return includedSessions;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public long getPriceXof() {
        return priceXof;
    }

    public boolean isActive() {
        return active;
    }
}
