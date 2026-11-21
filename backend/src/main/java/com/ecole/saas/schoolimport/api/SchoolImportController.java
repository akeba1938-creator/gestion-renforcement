package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.DuplicateStrategy;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.schoolimport.application.SchoolImportJobService;
import com.ecole.saas.schoolimport.application.SchoolImportJobService.CreateImportJobCommand;
import com.ecole.saas.schoolimport.application.SchoolImportJobService.DownloadableCsv;
import com.ecole.saas.schoolimport.application.SchoolImportJobService.ImportJobResult;
import com.ecole.saas.schoolimport.domain.SchoolImportValidationException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/schoolimport")
public class SchoolImportController {

    private static final MediaType TEXT_CSV_UTF8 = MediaType.parseMediaType("text/csv; charset=UTF-8");

    private final SchoolImportJobService jobService;

    public SchoolImportController(SchoolImportJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ImportTemplateResponse>> listTemplates() {
        List<ImportTemplateResponse> templates = jobService.listTemplates().stream()
                .map(ImportTemplateResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(templates);
    }

    @GetMapping("/templates/{importType}/download")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable ImportType importType) {
        DownloadableCsv csv = jobService.downloadTemplate(importType);
        return buildCsvDownloadResponse(csv);
    }

    @PostMapping(value = "/jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobResponse> createImportJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestPart("file") MultipartFile file,
            @RequestParam ImportType importType,
            @RequestParam(required = false) ImportValidationMode validationMode,
            @RequestParam(required = false) DuplicateStrategy duplicateStrategy,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID evaluationId,
            @RequestParam(required = false) UUID attendanceSessionId,
            @RequestParam(required = false) Boolean executeImmediately,
            @RequestParam(required = false) String delimiter,
            @RequestParam(required = false) String idempotencyKey,
            JwtAuthenticationToken authentication) {
        if (file == null || file.isEmpty()) {
            throw new SchoolImportValidationException(
                    "schoolimport-empty-file", "Fichier vide", "Le fichier à importer est obligatoire.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new SchoolImportValidationException("Impossible de lire le fichier téléversé.");
        }
        Character parsedDelimiter = null;
        if (delimiter != null && !delimiter.isEmpty()) {
            if (delimiter.length() != 1) {
                throw new SchoolImportValidationException("Le délimiteur CSV doit être un caractère unique.");
            }
            parsedDelimiter = delimiter.charAt(0);
        }

        String resolvedKey = SchoolImportApiSupport.resolveIdempotencyKey(idempotencyKeyHeader, idempotencyKey);
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);

        ImportJobResult result = jobService.createAndAnalyzeJob(
                new CreateImportJobCommand(
                        importType,
                        validationMode,
                        duplicateStrategy,
                        academicYearId,
                        evaluationId,
                        attendanceSessionId,
                        executeImmediately,
                        parsedDelimiter,
                        file.getOriginalFilename(),
                        bytes,
                        resolvedKey),
                actorId);

        ImportJobResponse body = ImportJobResponse.from(result.job());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/schoolimport/jobs/" + result.job().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/jobs")
    public ResponseEntity<PageResponse<ImportJobResponse>> listJobs(
            @RequestParam(required = false) ImportType importType,
            @RequestParam(required = false) ImportJobStatus status,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = SchoolImportPagination.of(
                page, size, sortBy, direction, SchoolImportPagination.JOB_SORT_FIELDS, "createdAt", Sort.Direction.DESC);
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        Page<ImportJobResponse> jobs = jobService
                .listJobs(importType, status, academicYearId, actorId, pageable)
                .map(ImportJobResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(jobs));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ImportJobResponse> getJob(
            @PathVariable UUID jobId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ImportJobResponse.from(jobService.getJob(jobId, actorId)));
    }

    @GetMapping("/jobs/{jobId}/rows")
    public ResponseEntity<PageResponse<ImportJobRowResponse>> listJobRows(
            @PathVariable UUID jobId,
            @RequestParam(required = false) ImportRowStatus rowStatus,
            @RequestParam(required = false) ImportRowAction plannedAction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "rowNumber") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = SchoolImportPagination.of(
                page, size, sortBy, direction, SchoolImportPagination.ROW_SORT_FIELDS, "rowNumber", Sort.Direction.ASC);
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        Page<ImportJobRowResponse> rows = jobService
                .listJobRows(jobId, rowStatus, plannedAction, actorId, pageable)
                .map(ImportJobRowResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(rows));
    }

    @GetMapping("/jobs/{jobId}/events")
    public ResponseEntity<List<ImportJobEventResponse>> listJobEvents(
            @PathVariable UUID jobId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        List<ImportJobEventResponse> events = jobService.listJobEvents(jobId, actorId).stream()
                .map(ImportJobEventResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    @GetMapping("/jobs/{jobId}/errors/download")
    public ResponseEntity<byte[]> downloadErrorReport(
            @PathVariable UUID jobId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        DownloadableCsv csv = jobService.downloadErrorReport(jobId, actorId);
        return buildCsvDownloadResponse(csv);
    }

    @PostMapping("/jobs/{jobId}/execute")
    public ResponseEntity<ImportJobResponse> executeJob(
            @PathVariable UUID jobId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody ExecuteImportJobRequest request,
            JwtAuthenticationToken authentication) {
        if (!Boolean.TRUE.equals(request.confirmExecution())) {
            throw new SchoolImportValidationException(
                    "Vous devez confirmer explicitement l'exécution du lot (confirmExecution = true).");
        }
        String resolvedKey = SchoolImportApiSupport.resolveIdempotencyKey(
                idempotencyKeyHeader, request.idempotencyKey());
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        ImportJobResult result = jobService.executeJob(jobId, resolvedKey, actorId);
        ImportJobResponse body = ImportJobResponse.from(result.job());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<ImportJobResponse> cancelJob(
            @PathVariable UUID jobId,
            @Valid @RequestBody CancelImportJobRequest request,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolImportApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ImportJobResponse.from(jobService.cancelJob(jobId, request.reason(), actorId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<SchoolImportSummaryResponse> getSummary() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolImportSummaryResponse.from(jobService.getSummary()));
    }

    private static ResponseEntity<byte[]> buildCsvDownloadResponse(DownloadableCsv csv) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(csv.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(TEXT_CSV_UTF8)
                .contentLength(csv.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv.content());
    }
}
