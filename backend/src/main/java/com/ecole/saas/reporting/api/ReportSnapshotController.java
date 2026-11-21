package com.ecole.saas.reporting.api;

import com.ecole.saas.reporting.ReportFormat;
import com.ecole.saas.reporting.ReportSnapshotStatus;
import com.ecole.saas.reporting.ReportType;
import com.ecole.saas.reporting.application.ReportSnapshotService;
import com.ecole.saas.reporting.application.ReportSnapshotService.GenerateSnapshotCommand;
import com.ecole.saas.reporting.application.ReportSnapshotService.SnapshotResult;
import com.ecole.saas.reporting.application.ReportingModels.DownloadableReport;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST des snapshots persistés de rapports consolidés (idempotence, consultation,
 * téléchargement SHA-256 vérifié, historique immuable et archivage logique).
 */
@RestController
@RequestMapping("/api/v1/reporting/snapshots")
public class ReportSnapshotController {

    private final ReportSnapshotService snapshotService;

    public ReportSnapshotController(ReportSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public ResponseEntity<ReportSnapshotResponse> generateSnapshot(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody GenerateReportSnapshotRequest request,
            JwtAuthenticationToken authentication) {
        String idempotencyKey = ReportingApiSupport.resolveIdempotencyKey(
                idempotencyKeyHeader, request.idempotencyKey());
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        SnapshotResult result = snapshotService.generateSnapshot(
                new GenerateSnapshotCommand(
                        request.reportType(),
                        request.outputFormat(),
                        request.academicYearId(),
                        request.evaluationPeriodId(),
                        request.classRoomId(),
                        request.studentId(),
                        request.fromDate(),
                        request.toDate(),
                        request.title(),
                        idempotencyKey),
                actorId);
        ReportSnapshotResponse body = ReportSnapshotResponse.from(result.snapshot());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/reporting/snapshots/" + result.snapshot().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ReportSnapshotResponse>> listSnapshots(
            @RequestParam(required = false) ReportType reportType,
            @RequestParam(required = false) ReportFormat outputFormat,
            @RequestParam(required = false) ReportSnapshotStatus status,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "generatedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = ReportingPagination.of(
                page,
                size,
                sortBy,
                direction,
                ReportingPagination.SNAPSHOT_SORT_FIELDS,
                "generatedAt",
                Sort.Direction.DESC);
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        Page<ReportSnapshotResponse> snapshots = snapshotService
                .listSnapshots(reportType, outputFormat, status, academicYearId, actorId, pageable)
                .map(ReportSnapshotResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(snapshots));
    }

    @GetMapping("/{snapshotId}")
    public ResponseEntity<ReportSnapshotResponse> getSnapshot(
            @PathVariable UUID snapshotId,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportSnapshotResponse.from(snapshotService.getSnapshot(snapshotId, actorId)));
    }

    @GetMapping("/{snapshotId}/download")
    public ResponseEntity<byte[]> downloadSnapshot(
            @PathVariable UUID snapshotId,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        DownloadableReport report = snapshotService.downloadSnapshot(snapshotId, actorId);
        return ReportingController.buildDownloadResponse(report);
    }

    @GetMapping("/{snapshotId}/events")
    public ResponseEntity<List<ReportSnapshotEventResponse>> listSnapshotEvents(
            @PathVariable UUID snapshotId,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        List<ReportSnapshotEventResponse> events = snapshotService.listSnapshotEvents(snapshotId, actorId).stream()
                .map(ReportSnapshotEventResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    @PostMapping("/{snapshotId}/archive")
    public ResponseEntity<ReportSnapshotResponse> archiveSnapshot(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody ArchiveReportSnapshotRequest request,
            JwtAuthenticationToken authentication) {
        UUID actorId = ReportingApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ReportSnapshotResponse.from(
                        snapshotService.archiveSnapshot(snapshotId, request.reason(), actorId)));
    }
}
