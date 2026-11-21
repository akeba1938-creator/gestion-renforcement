package com.ecole.saas.reporting.application;

import com.ecole.saas.reporting.domain.ReportSecurityPolicy;
import com.ecole.saas.reporting.domain.ReportingNotFoundException;
import com.ecole.saas.school.SchoolDirectory;
import com.ecole.saas.school.SchoolProfileSnapshot;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.subscription.QuotaResource;
import com.ecole.saas.subscription.SubscriptionAccessPort;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Garde d'abonnement, de quota {@code STORAGE_MB} et de contexte établissement (fuseau horaire)
 * pour le module {@code reporting}.
 */
@Component
class ReportingSubscriptionGuard {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Dakar");

    private final SubscriptionAccessPort subscriptionAccessPort;
    private final SchoolDirectory schoolDirectory;

    ReportingSubscriptionGuard(
            SubscriptionAccessPort subscriptionAccessPort,
            SchoolDirectory schoolDirectory) {
        this.subscriptionAccessPort = subscriptionAccessPort;
        this.schoolDirectory = schoolDirectory;
    }

    void assertReadAllowed() {
        subscriptionAccessPort.assertReadAllowed();
    }

    void assertWriteAllowed() {
        subscriptionAccessPort.requireOperationalSubscription();
    }

    void assertStorageQuotaAvailableForNewSnapshot(long currentReportingBytes, long additionalBytes) {
        long currentTotalMiB = subscriptionAccessPort.currentUsage(QuotaResource.STORAGE_MB);
        long currentReportingMiB = ReportSecurityPolicy.bytesToCeilingMiB(currentReportingBytes);
        long projectedReportingMiB = ReportSecurityPolicy.bytesToCeilingMiB(
                Math.addExact(currentReportingBytes, additionalBytes));
        long deltaMiB = Math.max(0L, projectedReportingMiB - currentReportingMiB);
        subscriptionAccessPort.assertQuotaAvailable(QuotaResource.STORAGE_MB, currentTotalMiB, deltaMiB);
    }

    SchoolProfileSnapshot requireSchoolProfile() {
        TenantId tenantId = TenantContext.requireCurrent();
        return schoolDirectory.findProfileById(tenantId.value())
                .orElseThrow(() -> new ReportingNotFoundException(
                        "reporting-school-not-found",
                        "Impossible de charger le profil de l'établissement courant."));
    }

    ZoneId resolveSchoolZone(SchoolProfileSnapshot profile) {
        if (profile.timezone() == null || profile.timezone().isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(profile.timezone());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }
}
