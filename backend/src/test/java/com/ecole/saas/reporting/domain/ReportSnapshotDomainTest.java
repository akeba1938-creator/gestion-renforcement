package com.ecole.saas.reporting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportScopeType;
import com.ecole.saas.reporting.ReportSnapshotEventType;
import com.ecole.saas.reporting.ReportSnapshotStatus;
import com.ecole.saas.reporting.ReportType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportSnapshotDomainTest {

    @Test
    void shouldFormatNumbersBuildSafeKeysAndVerifySha256Integrity() {
        String number = ReportSecurityPolicy.formatSnapshotNumber("ecole_dkr", 2026, 7);
        assertThat(number).isEqualTo("RPT-ECOLE_DKR-2026-000007");

        UUID tenantId = UUID.randomUUID();
        String key = ReportSecurityPolicy.buildTenantScopedStorageKey(tenantId, 2026, number, ReportFormat.CSV);
        assertThat(key).isEqualTo("tenants/" + tenantId + "/reporting/2026/" + number + ".csv");

        byte[] payload = "col1,col2\r\nval1,val2\r\n".getBytes(StandardCharsets.UTF_8);
        String sha = ReportSecurityPolicy.sha256Hex(payload);
        ReportSecurityPolicy.assertIntegrity(payload, sha);

        assertThatThrownBy(() -> ReportSecurityPolicy.assertIntegrity(payload, "0".repeat(64)))
                .isInstanceOf(ReportingConflictException.class);
        assertThatThrownBy(() -> ReportSecurityPolicy.validateStorageObjectKey("../etc/passwd"))
                .isInstanceOf(ReportingValidationException.class);
        assertThat(ReportSecurityPolicy.bytesToCeilingMiB(1L)).isEqualTo(1L);
        assertThat(ReportSecurityPolicy.bytesToCeilingMiB(0L)).isZero();
    }

    @Test
    void shouldEnforceSnapshotArchivalLifecycleAndImmutabilityCallbacks() {
        byte[] content = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        String sha = ReportSecurityPolicy.sha256Hex(content);
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-21T10:00:00Z");

        ReportSnapshot snapshot = new ReportSnapshot(
                "RPT-DKR-2026-000001",
                "idem-snap-1",
                ReportSecurityPolicy.sha256Hex("hash-input"),
                ReportType.FINANCIAL,
                ReportFormat.JSON,
                ReportScopeType.SCHOOL,
                UUID.randomUUID(),
                null,
                null,
                null,
                null,
                null,
                "Rapport financier T1",
                "RPT-DKR-2026-000001.json",
                ReportFormat.JSON.contentType(),
                "tenants/" + UUID.randomUUID() + "/reporting/2026/RPT-DKR-2026-000001.json",
                content.length,
                sha,
                "{\"totalNetXof\":100000}",
                actorId,
                now);

        assertThat(snapshot.getStatus()).isEqualTo(ReportSnapshotStatus.READY);

        assertThatThrownBy(() -> snapshot.archive(actorId, now.plusSeconds(60), "abc"))
                .isInstanceOf(ReportingValidationException.class);

        snapshot.archive(actorId, now.plusSeconds(120), "Clôture trimestrielle archivée");
        assertThat(snapshot.getStatus()).isEqualTo(ReportSnapshotStatus.ARCHIVED);
        assertThat(snapshot.getArchiveReason()).isEqualTo("Clôture trimestrielle archivée");

        assertThatThrownBy(() -> snapshot.archive(actorId, now.plusSeconds(180), "Doublon archivage"))
                .isInstanceOf(ReportingConflictException.class);

        ReportSnapshotContent binary = new ReportSnapshotContent(
                UUID.randomUUID(),
                snapshot.getStorageObjectKey(),
                ReportFormat.JSON.contentType(),
                ReportFormat.JSON,
                sha,
                content);
        assertThatThrownBy(binary::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(binary::onRemove).isInstanceOf(UnsupportedOperationException.class);

        ReportSnapshotEvent event = new ReportSnapshotEvent(
                UUID.randomUUID(), 1, ReportSnapshotEventType.GENERATED, actorId, "Généré", now);
        assertThatThrownBy(event::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(event::onRemove).isInstanceOf(UnsupportedOperationException.class);
    }
}
