package com.ecole.saas.schoolimport.application;

import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.schoolimport.domain.ImportFileSecurityPolicy;
import com.ecole.saas.schoolimport.domain.SchoolImportConflictException;
import com.ecole.saas.schoolimport.domain.SchoolImportNotFoundException;
import com.ecole.saas.schoolimport.infrastructure.ImportJobFileRepository;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.subscription.QuotaCheckResult;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Garde d'abonnement, d'état d'établissement, de fuseau horaire (défaut {@code Africa/Dakar})
 * et de quotas ({@link QuotaResource#STUDENTS} et {@link QuotaResource#STORAGE_MB}) pour {@code schoolimport}.
 */
@Component
class SchoolImportSubscriptionGuard {

    static final ZoneId DEFAULT_SCHOOL_ZONE = ZoneId.of("Africa/Dakar");

    private final SubscriptionAccessPort subscriptionAccessPort;
    private final SchoolDirectory schoolDirectory;
    private final ImportJobFileRepository fileRepository;

    SchoolImportSubscriptionGuard(
            SubscriptionAccessPort subscriptionAccessPort,
            SchoolDirectory schoolDirectory,
            ImportJobFileRepository fileRepository) {
        this.subscriptionAccessPort = subscriptionAccessPort;
        this.schoolDirectory = schoolDirectory;
        this.fileRepository = fileRepository;
    }

    void requireReadAccess() {
        subscriptionAccessPort.assertReadAllowed();
    }

    SchoolProfileSnapshot requireWriteAccess() {
        subscriptionAccessPort.requireOperationalSubscription();
        SchoolProfileSnapshot school = requireSchoolProfile();
        if (!school.active()) {
            throw new SchoolImportConflictException(
                    "school-inactive",
                    "L'établissement est actuellement inactif : les opérations d'import sont bloquées.");
        }
        return school;
    }

    void assertStorageQuotaAvailable(long additionalBytes) {
        long ownCurrentBytes = fileRepository.sumFileSizeBytes();
        long ownCurrentMiB = ImportFileSecurityPolicy.bytesToCeilingMiB(ownCurrentBytes);
        long ownProjectedBytes = Math.addExact(ownCurrentBytes, Math.max(0L, additionalBytes));
        long ownProjectedMiB = ImportFileSecurityPolicy.bytesToCeilingMiB(ownProjectedBytes);
        long additionalMiB = Math.max(0L, ownProjectedMiB - ownCurrentMiB);
        long totalCurrentMiB = subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB);
        subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STORAGE_MB, totalCurrentMiB, additionalMiB);
    }

    QuotaCheckResult evaluateStudentQuota(long currentActiveStudents, long plannedNewStudents) {
        return subscriptionAccessPort.checkQuota(
                QuotaResource.STUDENTS, currentActiveStudents, Math.max(0L, plannedNewStudents));
    }

    void assertStudentQuotaAvailable(long currentActiveStudents, long plannedNewStudents) {
        if (plannedNewStudents <= 0L) {
            return;
        }
        subscriptionAccessPort.assertQuotaAvailable(
                QuotaResource.STUDENTS, currentActiveStudents, plannedNewStudents);
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        UUID tenantId = TenantContext.requireCurrent().value();
        return schoolDirectory.findProfileById(tenantId)
                .orElseThrow(() -> new SchoolImportNotFoundException("Établissement introuvable."));
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
