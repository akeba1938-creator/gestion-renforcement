package com.ecole.saas.audit.api;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.application.AuditSearchFilter;
import com.ecole.saas.audit.application.PlatformAuditQueryService;
import com.ecole.saas.audit.application.PlatformAuditSearchFilter;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST strictement en lecture seule d'audit plateforme ({@code ADMIN_SAAS}) :
 * <ul>
 *   <li>Consultation du journal plateforme {@code platform_audit_logs}.</li>
 *   <li>Consultation supervisée du journal d'un établissement cible avec motif obligatoire journalisé.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformAuditController {

    private final PlatformAuditQueryService platformAuditQueryService;

    public PlatformAuditController(PlatformAuditQueryService platformAuditQueryService) {
        this.platformAuditQueryService = platformAuditQueryService;
    }

    @GetMapping("/audit/logs")
    public ResponseEntity<PageResponse<PlatformAuditLogResponse>> listPlatformAuditLogs(
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActorType actorType,
            @RequestParam(required = false) UUID targetSchoolId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = AuditPagination.of(page, size, sortBy, direction);
        PlatformAuditSearchFilter filter = new PlatformAuditSearchFilter(
                category,
                action,
                outcome,
                severity,
                actorId,
                actorType,
                targetSchoolId,
                resourceType,
                resourceId,
                correlationId,
                from,
                to,
                search);
        Page<PlatformAuditLogResponse> result = platformAuditQueryService
                .searchPlatformAuditLogs(filter, pageable)
                .map(PlatformAuditLogResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/audit/logs/{auditLogId}")
    public ResponseEntity<PlatformAuditLogResponse> getPlatformAuditLog(@PathVariable UUID auditLogId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PlatformAuditLogResponse.from(platformAuditQueryService.getPlatformAuditLog(auditLogId)));
    }

    @GetMapping("/schools/{schoolId}/audit/logs")
    public ResponseEntity<PageResponse<AuditLogResponse>> listSchoolAuditLogs(
            @PathVariable UUID schoolId,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(required = false) AuditSeverity severity,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActorType actorType,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = AuditPagination.of(page, size, sortBy, direction);
        AuditSearchFilter filter = new AuditSearchFilter(
                category,
                action,
                outcome,
                severity,
                actorId,
                actorType,
                resourceType,
                resourceId,
                correlationId,
                from,
                to,
                search);
        Page<AuditLogResponse> result = platformAuditQueryService
                .searchSchoolAuditLogs(schoolId, reason, filter, pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }
}
