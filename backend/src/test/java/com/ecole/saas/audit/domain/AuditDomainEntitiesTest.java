package com.ecole.saas.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditDomainEntitiesTest {

    @Test
    void shouldConstructNormalizedImmutableTenantAuditLog() {
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T10:30:00Z");

        AuditLog log = new AuditLog(
                AuditCategory.FINANCE,
                AuditAction.PAYMENT_RECORDED,
                AuditOutcome.SUCCESS,
                AuditSeverity.INFO,
                AuditActorType.USER,
                actorId,
                "CASHIER",
                " payment ",
                resourceId,
                "Encaissement avec token=secret123456",
                Map.of("amountXof", 50_000L, "password", "never-stored"),
                "req-123",
                now);

        assertThat(log.getResourceType()).isEqualTo("PAYMENT");
        assertThat(log.getSummary()).doesNotContain("secret123456").contains(AuditMetadataSanitizer.MASK);
        assertThat(log.parsedMetadata())
                .containsOnlyKeys("amountXof")
                .containsEntry("amountXof", "50000");

        assertThatThrownBy(log::onUpdate)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(log::onRemove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldConstructNormalizedImmutablePlatformAuditLog() {
        UUID adminId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T10:30:00Z");

        PlatformAuditLog log = new PlatformAuditLog(
                AuditCategory.AUDIT,
                AuditAction.AUDIT_TENANT_LOGS_ACCESSED,
                AuditOutcome.SUCCESS,
                AuditSeverity.WARNING,
                AuditActorType.PLATFORM_ADMIN,
                adminId,
                "ADMIN_SAAS",
                schoolId,
                "audit_log",
                schoolId,
                "Consultation supervisée",
                "Investigation incident ticket SUP-42",
                Map.of("targetSchoolId", schoolId.toString(), "cni", "1990123456789"),
                "corr-99",
                now);

        assertThat(log.getResourceType()).isEqualTo("AUDIT_LOG");
        assertThat(log.getReason()).isEqualTo("Investigation incident ticket SUP-42");
        assertThat(log.parsedMetadata()).containsOnlyKeys("targetSchoolId");

        assertThatThrownBy(log::onUpdate)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(log::onRemove)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldValidateAuditQueryCriteriaAndSupportAccessReason() {
        Instant t1 = Instant.parse("2026-10-10T00:00:00Z");
        Instant t2 = Instant.parse("2026-10-11T00:00:00Z");

        AuditQueryCriteria.validateDateRange(t1, t2);
        AuditQueryCriteria.validateDateRange(t1, t1);

        assertThatThrownBy(() -> AuditQueryCriteria.validateDateRange(t2, t1))
                .isInstanceOf(AuditValidationException.class);

        assertThatThrownBy(() -> AuditQueryCriteria.requireSupportAccessReason(null))
                .isInstanceOf(AuditValidationException.class);
        assertThatThrownBy(() -> AuditQueryCriteria.requireSupportAccessReason("court"))
                .isInstanceOf(AuditValidationException.class);

        String valid = AuditQueryCriteria.requireSupportAccessReason("  Support ticket #2026-88 : vérification paiement  ");
        assertThat(valid).isEqualTo("Support ticket #2026-88 : vérification paiement");
    }

    @Test
    void shouldRejectInvalidResourceType() {
        assertThatThrownBy(() -> new AuditLog(
                AuditCategory.SCHOOL,
                AuditAction.SCHOOL_UPDATED,
                AuditOutcome.SUCCESS,
                AuditSeverity.INFO,
                AuditActorType.SYSTEM,
                null,
                null,
                "invalid resource!",
                null,
                "Test",
                Map.of(),
                null,
                Instant.now()))
                .isInstanceOf(AuditValidationException.class);
    }
}
