package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Modèle réutilisable de document scolaire ou administratif isolé par établissement.
 * Archivage logique via {@code active = false}. Frais éventuels en entiers FCFA ({@code long}).
 */
@Entity
@Table(name = "schooldocs_templates")
public class DocumentTemplate extends TenantScopedEntity {

    private static final String CODE_REGEX = "^[A-Z0-9][A-Z0-9_-]{1,49}$";
    private static final int MAX_VALIDITY_DAYS = 3650;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "title_template", nullable = false, length = 255)
    private String titleTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(name = "footer_template", length = 1000)
    private String footerTemplate;

    @Column(name = "default_validity_days")
    private Integer defaultValidityDays;

    @Column(name = "fee_amount_xof", nullable = false)
    private long feeAmountXof;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected DocumentTemplate() {
    }

    public DocumentTemplate(
            String code,
            String name,
            String description,
            DocumentType documentType,
            String language,
            String titleTemplate,
            String bodyTemplate,
            String footerTemplate,
            Integer defaultValidityDays,
            long feeAmountXof) {
        this.code = normalizeCode(code);
        this.name = requireText(name, "Le nom du modèle", 150);
        this.description = normalizeOptional(description, 500);
        this.documentType = Objects.requireNonNull(documentType, "Le type de document est obligatoire.");
        this.language = normalizeLanguage(language);
        this.titleTemplate = requireText(titleTemplate, "Le modèle de titre", 255);
        this.bodyTemplate = requireBody(bodyTemplate);
        this.footerTemplate = normalizeOptional(footerTemplate, 1000);
        this.defaultValidityDays = validateValidityDays(defaultValidityDays);
        this.feeAmountXof = validateFeeXof(feeAmountXof);
        this.active = true;
    }

    public void update(
            String name,
            String description,
            DocumentType documentType,
            String language,
            String titleTemplate,
            String bodyTemplate,
            String footerTemplate,
            Integer defaultValidityDays,
            long feeAmountXof) {
        this.name = requireText(name, "Le nom du modèle", 150);
        this.description = normalizeOptional(description, 500);
        this.documentType = Objects.requireNonNull(documentType, "Le type de document est obligatoire.");
        this.language = normalizeLanguage(language);
        this.titleTemplate = requireText(titleTemplate, "Le modèle de titre", 255);
        this.bodyTemplate = requireBody(bodyTemplate);
        this.footerTemplate = normalizeOptional(footerTemplate, 1000);
        this.defaultValidityDays = validateValidityDays(defaultValidityDays);
        this.feeAmountXof = validateFeeXof(feeAmountXof);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new SchoolDocValidationException("Le code du modèle de document est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches(CODE_REGEX)) {
            throw new SchoolDocValidationException(
                    "Le code du modèle doit contenir entre 2 et 50 caractères (A-Z, 0-9, '_' ou '-').");
        }
        return normalized;
    }

    private static String requireText(String raw, String label, int maxLength) {
        if (raw == null || raw.isBlank()) {
            throw new SchoolDocValidationException(label + " est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new SchoolDocValidationException(label + " ne doit pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String requireBody(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SchoolDocValidationException("Le corps du modèle de document est obligatoire.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 20000) {
            throw new SchoolDocValidationException("Le corps du modèle ne doit pas dépasser 20 000 caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new SchoolDocValidationException("La valeur dépasse la limite de " + maxLength + " caractères.");
        }
        return trimmed;
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "fr";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[a-z]{2}(-[a-z]{2})?$") || normalized.length() > 10) {
            throw new SchoolDocValidationException("Le code langue du modèle est invalide (ex. fr, en).");
        }
        return normalized;
    }

    private static Integer validateValidityDays(Integer days) {
        if (days == null) {
            return null;
        }
        if (days < 1 || days > MAX_VALIDITY_DAYS) {
            throw new SchoolDocValidationException(
                    "La durée de validité par défaut doit être comprise entre 1 et " + MAX_VALIDITY_DAYS + " jours.");
        }
        return days;
    }

    private static long validateFeeXof(long feeXof) {
        if (feeXof < 0L) {
            throw new SchoolDocValidationException("Les frais de document en FCFA doivent être positifs ou nuls.");
        }
        return feeXof;
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

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getLanguage() {
        return language;
    }

    public String getTitleTemplate() {
        return titleTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public String getFooterTemplate() {
        return footerTemplate;
    }

    public Integer getDefaultValidityDays() {
        return defaultValidityDays;
    }

    public long getFeeAmountXof() {
        return feeAmountXof;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
