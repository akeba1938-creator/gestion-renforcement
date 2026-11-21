package com.ecole.saas.communication.application;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditLogPort;
import com.ecole.saas.audit.AuditRecordCommand;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ConsentSource;
import com.ecole.saas.communication.domain.ContactPreference;
import com.ecole.saas.communication.domain.MessageTemplate;
import com.ecole.saas.communication.domain.RecipientType;
import com.ecole.saas.communication.infrastructure.ContactPreferenceRepository;
import com.ecole.saas.family.FamilyDirectoryPort;
import com.ecole.saas.family.GuardianContactSummary;
import com.ecole.saas.student.StudentContactSummary;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.teacher.TeacherDirectoryPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactPreferenceService {

    private final ContactPreferenceRepository preferenceRepository;
    private final AudienceResolver audienceResolver;
    private final FamilyDirectoryPort familyDirectoryPort;
    private final StudentDirectoryPort studentDirectoryPort;
    private final TeacherDirectoryPort teacherDirectoryPort;
    private final CommunicationSubscriptionGuard subscriptionGuard;
    private final AuditLogPort auditLogPort;
    private final Clock clock;

    public ContactPreferenceService(
            ContactPreferenceRepository preferenceRepository,
            AudienceResolver audienceResolver,
            FamilyDirectoryPort familyDirectoryPort,
            StudentDirectoryPort studentDirectoryPort,
            TeacherDirectoryPort teacherDirectoryPort,
            CommunicationSubscriptionGuard subscriptionGuard,
            AuditLogPort auditLogPort,
            Clock clock) {
        this.preferenceRepository = preferenceRepository;
        this.audienceResolver = audienceResolver;
        this.familyDirectoryPort = familyDirectoryPort;
        this.studentDirectoryPort = studentDirectoryPort;
        this.teacherDirectoryPort = teacherDirectoryPort;
        this.subscriptionGuard = subscriptionGuard;
        this.auditLogPort = auditLogPort;
        this.clock = clock;
    }

    @PreAuthorize(CommunicationRoles.PREFERENCE_ADMIN_ROLES)
    @Transactional(readOnly = true)
    public ContactPreferenceView getPreference(RecipientType contactType, UUID contactId) {
        subscriptionGuard.requireReadAccess();
        audienceResolver.assertContactExists(contactType, contactId);
        return preferenceRepository.findByContactTypeAndContactId(contactType, contactId)
                .map(ContactPreferenceView::fromEntity)
                .orElseGet(() -> ContactPreferenceView.defaultFor(contactType, contactId));
    }

    @PreAuthorize(CommunicationRoles.PREFERENCE_ADMIN_ROLES)
    @Transactional
    public ContactPreferenceView upsertPreference(
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
            UUID actorUserId) {
        subscriptionGuard.requireWriteAccess();
        audienceResolver.assertContactExists(contactType, contactId);
        return savePreference(
                contactType,
                contactId,
                inAppEnabled,
                smsEnabled,
                whatsappEnabled,
                emailEnabled,
                marketingOptIn,
                preferredChannel,
                preferredLanguage,
                quietHoursStart,
                quietHoursEnd,
                consentSource != null ? consentSource : ConsentSource.ADMIN_BACKOFFICE,
                actorUserId);
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional(readOnly = true)
    public ContactPreferenceView getMyPreference(UUID currentUserId) {
        subscriptionGuard.requireReadAccess();
        SelfContactTarget target = resolveSelfTarget(currentUserId);
        return preferenceRepository.findByContactTypeAndContactId(target.type(), target.id())
                .map(ContactPreferenceView::fromEntity)
                .orElseGet(() -> ContactPreferenceView.defaultFor(target.type(), target.id()));
    }

    @PreAuthorize(CommunicationRoles.INBOX_ROLES)
    @Transactional
    public ContactPreferenceView updateMyPreference(
            UUID currentUserId,
            boolean inAppEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean emailEnabled,
            boolean marketingOptIn,
            CommunicationChannel preferredChannel,
            String preferredLanguage,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd) {
        subscriptionGuard.requireWriteAccess();
        SelfContactTarget target = resolveSelfTarget(currentUserId);
        return savePreference(
                target.type(),
                target.id(),
                inAppEnabled,
                smsEnabled,
                whatsappEnabled,
                emailEnabled,
                marketingOptIn,
                preferredChannel,
                preferredLanguage,
                quietHoursStart,
                quietHoursEnd,
                ConsentSource.SELF_SERVICE,
                currentUserId);
    }

    private ContactPreferenceView savePreference(
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
            UUID actorUserId) {
        Instant now = clock.instant();
        Optional<ContactPreference> existing =
                preferenceRepository.findByContactTypeAndContactId(contactType, contactId);
        ContactPreference preference;
        if (existing.isPresent()) {
            preference = existing.get();
            preference.updateSettings(
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
                    now,
                    actorUserId);
        } else {
            preference = new ContactPreference(
                    contactType,
                    contactId,
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
                    now,
                    actorUserId);
        }
        ContactPreference saved = preferenceRepository.save(preference);
        auditLogPort.recordTenantEvent(AuditRecordCommand.success(
                AuditCategory.COMMUNICATION,
                AuditAction.COMMUNICATION_PREFERENCE_UPDATED,
                "CONTACT_PREFERENCE",
                saved.getId(),
                "Mise à jour des préférences de contact (" + contactType + ").",
                Map.of(
                        "contactType", contactType.name(),
                        "contactId", contactId.toString(),
                        "smsEnabled", smsEnabled,
                        "whatsappEnabled", whatsappEnabled,
                        "emailEnabled", emailEnabled,
                        "inAppEnabled", inAppEnabled,
                        "marketingOptIn", marketingOptIn,
                        "consentSource", saved.getConsentSource().name())));
        return ContactPreferenceView.fromEntity(saved);
    }

    private SelfContactTarget resolveSelfTarget(UUID currentUserId) {
        Optional<GuardianContactSummary> guardian = familyDirectoryPort.findGuardianContactByUserId(currentUserId);
        if (guardian.isPresent()) {
            return new SelfContactTarget(RecipientType.GUARDIAN, guardian.get().id());
        }
        Optional<StudentContactSummary> student = studentDirectoryPort.findStudentContactByUserId(currentUserId);
        if (student.isPresent()) {
            return new SelfContactTarget(RecipientType.STUDENT, student.get().id());
        }
        Optional<TeacherSummary> teacher = teacherDirectoryPort.findTeacherByUserId(currentUserId);
        if (teacher.isPresent()) {
            return new SelfContactTarget(RecipientType.TEACHER, teacher.get().id());
        }
        return new SelfContactTarget(RecipientType.STAFF_USER, currentUserId);
    }

    private record SelfContactTarget(RecipientType type, UUID id) {
    }

    public record ContactPreferenceView(
            UUID id,
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
            Instant consentRecordedAt,
            boolean customized) {

        static ContactPreferenceView fromEntity(ContactPreference entity) {
            return new ContactPreferenceView(
                    entity.getId(),
                    entity.getContactType(),
                    entity.getContactId(),
                    entity.isInAppEnabled(),
                    entity.isSmsEnabled(),
                    entity.isWhatsappEnabled(),
                    entity.isEmailEnabled(),
                    entity.isMarketingOptIn(),
                    entity.getPreferredChannel(),
                    entity.getPreferredLanguage(),
                    entity.getQuietHoursStart(),
                    entity.getQuietHoursEnd(),
                    entity.getConsentSource(),
                    entity.getConsentRecordedAt(),
                    true);
        }

        static ContactPreferenceView defaultFor(RecipientType type, UUID id) {
            return new ContactPreferenceView(
                    null,
                    type,
                    id,
                    true,
                    true,
                    true,
                    true,
                    true,
                    CommunicationChannel.IN_APP,
                    MessageTemplate.DEFAULT_LANGUAGE,
                    null,
                    null,
                    ConsentSource.ADMIN_BACKOFFICE,
                    null,
                    false);
        }
    }
}
