package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentStatus;
import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.application.IssuedDocumentService;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.DocumentView;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.DownloadableDocument;
import com.ecole.saas.schooldocs.application.IssuedDocumentService.IssueDocumentCommand;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schooldocs")
public class IssuedDocumentController {

    private final IssuedDocumentService documentService;

    public IssuedDocumentController(IssuedDocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/documents")
    public ResponseEntity<IssuedDocumentResponse> issueDocument(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody IssueDocumentRequest request,
            JwtAuthenticationToken authentication) {
        String idempotencyKey = SchoolDocsApiSupport.resolveIdempotencyKey(
                idempotencyKeyHeader, request.idempotencyKey());
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        DocumentView result = documentService.issueDocument(
                new IssueDocumentCommand(
                        request.templateId(),
                        request.documentType(),
                        request.studentId(),
                        request.guardianId(),
                        request.academicYearId(),
                        request.classRoomId(),
                        request.title(),
                        request.body(),
                        request.footer(),
                        request.variables(),
                        request.validityDays(),
                        request.feeAmountXof(),
                        idempotencyKey),
                actorId);
        IssuedDocumentResponse body = IssuedDocumentResponse.from(result);
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/schooldocs/documents/" + result.document().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @GetMapping("/documents")
    public ResponseEntity<PageResponse<IssuedDocumentResponse>> listDocuments(
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID guardianId,
            @RequestParam(required = false) UUID academicYearId,
            @RequestParam(required = false) UUID classRoomId,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) DocumentStatus status,
            @RequestParam(required = false) Boolean duplicate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "issuedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            JwtAuthenticationToken authentication) {
        Pageable pageable = SchoolDocsPagination.of(
                page, size, sortBy, direction, SchoolDocsPagination.DOCUMENT_SORT_FIELDS, "issuedAt", Sort.Direction.DESC);
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        Page<IssuedDocumentResponse> documents = documentService
                .listDocuments(
                        studentId,
                        guardianId,
                        academicYearId,
                        classRoomId,
                        documentType,
                        status,
                        duplicate,
                        actorId,
                        pageable)
                .map(IssuedDocumentResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(documents));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<IssuedDocumentResponse> getDocument(
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IssuedDocumentResponse.from(documentService.getDocument(documentId, actorId)));
    }

    @GetMapping("/documents/{documentId}/events")
    public ResponseEntity<List<DocumentEventResponse>> listDocumentEvents(
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        List<DocumentEventResponse> events = documentService.listDocumentEvents(documentId, actorId).stream()
                .map(DocumentEventResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(events);
    }

    @GetMapping("/documents/{documentId}/duplicates")
    public ResponseEntity<List<IssuedDocumentResponse>> listDuplicates(
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        List<IssuedDocumentResponse> chain = documentService.listDuplicates(documentId, actorId).stream()
                .map(IssuedDocumentResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(chain);
    }

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID documentId,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        DownloadableDocument file = documentService.downloadDocument(documentId, actorId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.fileSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Document-Number", file.documentNumber())
                .header("X-Document-Sha256", file.contentSha256())
                .body(file.content());
    }

    @PostMapping("/documents/{documentId}/reissue")
    public ResponseEntity<IssuedDocumentResponse> reissueDocument(
            @PathVariable UUID documentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody ReissueDocumentRequest request,
            JwtAuthenticationToken authentication) {
        String idempotencyKey = SchoolDocsApiSupport.resolveIdempotencyKey(
                idempotencyKeyHeader, request.idempotencyKey());
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        DocumentView result = documentService.reissueDocument(
                documentId,
                request.reason(),
                request.validityDays(),
                request.feeAmountXof(),
                idempotencyKey,
                actorId);
        IssuedDocumentResponse body = IssuedDocumentResponse.from(result);
        if (result.idempotentReplay()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header("X-Idempotent-Replay", "true")
                    .body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/schooldocs/documents/" + result.document().getId()))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @PostMapping("/documents/{documentId}/revoke")
    public ResponseEntity<IssuedDocumentResponse> revokeDocument(
            @PathVariable UUID documentId,
            @Valid @RequestBody RevokeDocumentRequest request,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        DocumentView revoked = documentService.revokeDocument(documentId, request.reason(), actorId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IssuedDocumentResponse.from(revoked));
    }

    @GetMapping("/verify/{verificationToken}")
    public ResponseEntity<DocumentVerificationResponse> verifyDocument(
            @PathVariable String verificationToken,
            JwtAuthenticationToken authentication) {
        UUID actorId = SchoolDocsApiSupport.extractActorId(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentVerificationResponse.from(documentService.verifyByToken(verificationToken, actorId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<SchoolDocsSummaryResponse> getSummary() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SchoolDocsSummaryResponse.from(documentService.getSummary()));
    }
}
