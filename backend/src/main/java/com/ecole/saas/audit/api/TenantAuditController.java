package com.ecole.saas.audit.api;

import com.ecole.saas.audit.AuditAction;
import com.ecole.saas.audit.AuditActorType;
import com.ecole.saas.audit.AuditCategory;
import com.ecole.saas.audit.AuditOutcome;
import com.ecole.saas.audit.AuditSeverity;
import com.ecole.saas.audit.application.AuditSearchFilter;
import com.ecole.saas.audit.application.TenantAuditQueryService;
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
 * API REST strictement en lecture seule de consultation du journal d'audit de l'établissement courant
 * ({@code PROMOTER}, {@code DIRECTOR}, {@code CENTER_MANAGER}).
 */
@RestController
@RequestMapping("/api/v1/audit")
public class TenantAuditController {

    private final TenantAuditQueryService tenantAuditQueryService;

    public TenantAuditController(TenantAuditQueryService tenantAuditQueryService) {
        this.tenantAuditQueryService = tenantAuditQueryService;
    }

    @GetMapping("/logs")
    public ResponseEntity<PageResponse<AuditLogResponse>> listAuditLogs(
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
        Page<AuditLogResponse> result = tenantAuditQueryService
                .searchAuditLogs(filter, pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/logs/{auditLogId}")
    public ResponseEntity<AuditLogResponse> getAuditLog(@PathVariable UUID auditLogId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AuditLogResponse.from(tenantAuditQueryService.getAuditLog(auditLogId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<AuditSummaryResponse> getAuditSummary(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AuditSummaryResponse.from(tenantAuditQueryService.getAuditSummary(from, to)));
    }
}
