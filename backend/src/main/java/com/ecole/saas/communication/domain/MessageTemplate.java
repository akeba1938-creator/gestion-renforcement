package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "communication_templates")
public class MessageTemplate extends TenantScopedEntity {

    public static final String DEFAULT_LANGUAGE = "fr";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,49}$");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private MessageCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private CommunicationChannel channel;

    @Column(name = "language", nullable = false, length = 10)
    private String language;

    @Column(name = "subject_template", length = 255)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false)
    private String bodyTemplate;

    @Column(name = "estimated_unit_cost_xof", nullable = false)
    private long estimatedUnitCostXof;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected MessageTemplate() {
    }

    public MessageTemplate(
            String code,
            String name,
            String description,
            MessageCategory category,
            CommunicationChannel channel,
            String language,
            String subjectTemplate,
            String bodyTemplate,
            long estimatedUnitCostXof) {
        this.code = normalizeCode(code);
        this.name = requireNonBlank(name, 120, "Le nom du modèle est obligatoire.");
        this.description = normalizeOptional(description, 500, "La description");
        this.category = Objects.requireNonNull(category, "La catégorie du modèle est obligatoire.");
        this.channel = Objects.requireNonNull(channel, "Le canal du modèle est obligatoire.");
        this.language = normalizeLanguage(language);
        this.subjectTemplate = validateSubjectForChannel(subjectTemplate, this.channel);
        this.bodyTemplate = requireNonBlank(bodyTemplate, 4000, "Le corps du modèle est obligatoire.");
        this.estimatedUnitCostXof = validateCostXof(estimatedUnitCostXof);
        this.active = true;
    }

    public void update(
            String name,
            String description,
            MessageCategory category,
            CommunicationChannel channel,
            String language,
            String subjectTemplate,
            String bodyTemplate,
            long estimatedUnitCostXof) {
        this.name = requireNonBlank(name, 120, "Le nom du modèle est obligatoire.");
        this.description = normalizeOptional(description, 500, "La description");
        this.category = Objects.requireNonNull(category, "La catégorie du modèle est obligatoire.");
        this.channel = Objects.requireNonNull(channel, "Le canal du modèle est obligatoire.");
        this.language = normalizeLanguage(language);
        this.subjectTemplate = validateSubjectForChannel(subjectTemplate, this.channel);
        this.bodyTemplate = requireNonBlank(bodyTemplate, 4000, "Le corps du modèle est obligatoire.");
        this.estimatedUnitCostXof = validateCostXof(estimatedUnitCostXof);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void assertActive() {
        if (!this.active) {
            throw new CommunicationConflictException(
                    "template-inactive",
                    "Ce modèle de message est désactivé et ne peut plus être utilisé pour un nouvel envoi.");
        }
    }

    public Set<String> requiredVariables() {
        Set<String> vars = new java.util.LinkedHashSet<>();
        vars.addAll(TemplateRenderer.extractVariables(subjectTemplate));
        vars.addAll(TemplateRenderer.extractVariables(bodyTemplate));
        return java.util.Collections.unmodifiableSet(vars);
    }

    public static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CommunicationValidationException("Le code du modèle est obligatoire.");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new CommunicationValidationException(
                    "Le code du modèle doit contenir entre 2 et 50 caractères (A-Z, 0-9, '_' ou '-').");
        }
        return normalized;
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 10 || !LANGUAGE_PATTERN.matcher(trimmed).matches()) {
            throw new CommunicationValidationException("La langue doit respecter un code ISO valide (ex. fr, en).");
        }
        return trimmed;
    }

    private static String validateSubjectForChannel(String rawSubject, CommunicationChannel channel) {
        String normalized = normalizeOptional(rawSubject, 255, "L'objet du modèle");
        if ((channel == CommunicationChannel.EMAIL || channel == CommunicationChannel.IN_APP) && normalized == null) {
            throw new CommunicationValidationException(
                    "L'objet du modèle est obligatoire pour le canal " + channel + ".");
        }
        return normalized;
    }

    private static long validateCostXof(long costXof) {
        if (costXof < 0) {
            throw new CommunicationValidationException("Le coût unitaire estimé en FCFA ne peut pas être négatif.");
        }
        return costXof;
    }

    private static String requireNonBlank(String raw, int maxLength, String blankMessage) {
        if (raw == null || raw.isBlank()) {
            throw new CommunicationValidationException(blankMessage);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new CommunicationValidationException(
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
            throw new CommunicationValidationException(
                    fieldLabel + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
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

    public MessageCategory getCategory() {
        return category;
    }

    public CommunicationChannel getChannel() {
        return channel;
    }

    public String getLanguage() {
        return language;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public long getEstimatedUnitCostXof() {
        return estimatedUnitCostXof;
    }

    public boolean isActive() {
        return active;
    }
}
