package com.ecole.saas.schooldocs.application;

import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.schooldocs.domain.DocumentSecurityPolicy;
import com.ecole.saas.schooldocs.domain.SchoolDocConflictException;
import com.ecole.saas.schooldocs.domain.SchoolDocNotFoundException;
import com.ecole.saas.schooldocs.infrastructure.IssuedDocumentRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Garde d'abonnement, d'état d'établissement, de fuseau horaire (défaut {@code Africa/Dakar})
 * et de quota stockage ({@link QuotaResource#STORAGE_MB}) pour le module {@code schooldocs}.
 */
@Component
class SchoolDocsSubscriptionGuard {

    static final ZoneId DEFAULT_SCHOOL_ZONE = ZoneId.of("Africa/Dakar");

    private final SubscriptionAccessPort subscriptionAccessPort;
    private final SchoolDirectory schoolDirectory;
    private final IssuedDocumentRepository issuedDocumentRepository;

    SchoolDocsSubscriptionGuard(
            SubscriptionAccessPort subscriptionAccessPort,
            SchoolDirectory schoolDirectory,
            IssuedDocumentRepository issuedDocumentRepository) {
        this.subscriptionAccessPort = subscriptionAccessPort;
        this.schoolDirectory = schoolDirectory;
        this.issuedDocumentRepository = issuedDocumentRepository;
    }

    void requireReadAccess() {
        subscriptionAccessPort.assertReadAllowed();
    }

    SchoolProfileSnapshot requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
        SchoolProfileSnapshot school = requireSchoolProfile();
        if (!school.active()) {
            throw new SchoolDocConflictException(
                    "school-inactive",
                    "L'établissement est actuellement inactif : les opérations d'écriture documentaire sont bloquées.");
        }
        return school;
    }

    void assertStorageQuotaAvailable(long additionalBytes) {
        long currentBytes = issuedDocumentRepository.sumFileSizeBytes();
        long currentMiB = DocumentSecurityPolicy.bytesToCeilingMiB(currentBytes);
        long projectedBytes = Math.addExact(currentBytes, Math.max(0L, additionalBytes));
        long projectedMiB = DocumentSecurityPolicy.bytesToCeilingMiB(projectedBytes);
        long additionalMiB = Math.max(0L, projectedMiB - currentMiB);
        subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STORAGE_MB, currentMiB, additionalMiB);
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        UUID tenantId = TenantContext.requireCurrent().value();
        return schoolDirectory.findProfileById(tenantId)
                .orElseThrow(() -> new SchoolDocNotFoundException("Établissement introuvable."));
    }

    ZoneId resolveSchoolZone(SchoolProfileSnapshot school) {
        if (school == null || school.timezone() == null || school.timezone().isBlank()) {
            return DEFAULT_SCHOOL_ZONE;
        }
        try {
            return ZoneId.of(school.timezone().trim());
        } catch (RuntimeException ex) {
            return DEFAULT_SCHOOL_ZONE;
        }
    }
}
