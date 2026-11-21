package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;
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
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "communication_contact_preferences")
public class ContactPreference extends TenantScopedEntity {

    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, updatable = false, length = 20)
    private RecipientType contactType;

    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "marketing_opt_in", nullable = false)
    private boolean marketingOptIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private CommunicationChannel preferredChannel;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_source", nullable = false, length = 30)
    private ConsentSource consentSource;

    @Column(name = "consent_recorded_at", nullable = false)
    private Instant consentRecordedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ContactPreference() {
    }

    public ContactPreference(
            RecipientType contactType,
            UUID contactId,
            boolean inAppEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean emailEnabled,
            boolean marketingOptIn,
            CommunicationChannel preferredChannel,
            String preferredLanguage,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            ConsentSource consentSource,
            Instant recordedAt,
            UUID updatedBy) {
        this.contactType = Objects.requireNonNull(contactType, "Le type de contact est obligatoire.");
        this.contactId = Objects.requireNonNull(contactId, "L'identifiant du contact est obligatoire.");
        applySettings(
                inAppEnabled,
                smsEnabled,
                whatsappEnabled,
                emailEnabled,
                marketingOptIn,
                preferredChannel,
                preferredLanguage,
                quietHoursStart,
                quietHoursEnd,
                consentSource,
                recordedAt,
                updatedBy);
    }

    public void updateSettings(
            boolean inAppEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean emailEnabled,
            boolean marketingOptIn,
            CommunicationChannel preferredChannel,
            String preferredLanguage,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            ConsentSource consentSource,
            Instant recordedAt,
            UUID updatedBy) {
        applySettings(
                inAppEnabled,
                smsEnabled,
                whatsappEnabled,
                emailEnabled,
                marketingOptIn,
                preferredChannel,
                preferredLanguage,
                quietHoursStart,
                quietHoursEnd,
                consentSource,
                recordedAt,
                updatedBy);
    }

    private void applySettings(
            boolean inAppEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean emailEnabled,
            boolean marketingOptIn,
            CommunicationChannel preferredChannel,
            String preferredLanguage,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            ConsentSource consentSource,
            Instant recordedAt,
            UUID updatedBy) {
        validateQuietHours(quietHoursStart, quietHoursEnd);
        this.inAppEnabled = inAppEnabled;
        this.smsEnabled = smsEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.emailEnabled = emailEnabled;
        this.marketingOptIn = marketingOptIn;
        this.preferredChannel = preferredChannel != null ? preferredChannel : CommunicationChannel.IN_APP;
        this.preferredLanguage = normalizeLanguage(preferredLanguage);
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.consentSource = consentSource != null ? consentSource : ConsentSource.ADMIN_BACKOFFICE;
        this.consentRecordedAt = Objects.requireNonNull(recordedAt, "La date d'enregistrement du consentement est obligatoire.");
        this.updatedBy = updatedBy;
    }

    public boolean isChannelEnabled(CommunicationChannel channel) {
        return switch (channel) {
            case IN_APP -> inAppEnabled;
            case SMS -> smsEnabled;
            case WHATSAPP -> whatsappEnabled;
            case EMAIL -> emailEnabled;
        };
    }

    private static void validateQuietHours(LocalTime start, LocalTime end) {
        if ((start == null) != (end == null)) {
            throw new CommunicationValidationException(
                    "Les heures de début et de fin de plage silencieuse doivent être renseignées ensemble.");
        }
        if (start != null && start.equals(end)) {
            throw new CommunicationValidationException(
                    "L'heure de début et l'heure de fin de plage silencieuse doivent être différentes.");
        }
    }

    private static String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return MessageTemplate.DEFAULT_LANGUAGE;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 10 || !LANGUAGE_PATTERN.matcher(trimmed).matches()) {
            throw new CommunicationValidationException("La langue préférée doit être un code ISO valide (ex. fr, en).");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public RecipientType getContactType() {
        return contactType;
    }

    public UUID getContactId() {
        return contactId;
    }

    public boolean isInAppEnabled() {
        return inAppEnabled;
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public boolean isWhatsappEnabled() {
        return whatsappEnabled;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isMarketingOptIn() {
        return marketingOptIn;
    }

    public CommunicationChannel getPreferredChannel() {
        return preferredChannel;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public ConsentSource getConsentSource() {
        return consentSource;
    }

    public Instant getConsentRecordedAt() {
        return consentRecordedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
