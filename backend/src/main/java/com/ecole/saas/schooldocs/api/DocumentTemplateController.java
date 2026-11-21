package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.application.DocumentTemplateService;
import com.ecole.saas.schooldocs.domain.DocumentTemplate;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schooldocs/templates")
public class DocumentTemplateController {

    private final DocumentTemplateService templateService;

    public DocumentTemplateController(DocumentTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public ResponseEntity<DocumentTemplateResponse> createTemplate(
            @Valid @RequestBody CreateDocumentTemplateRequest request) {
        DocumentTemplate created = templateService.createTemplate(
                request.code(),
                request.name(),
                request.description(),
                request.documentType(),
                request.language(),
                request.titleTemplate(),
                request.bodyTemplate(),
                request.footerTemplate(),
                request.defaultValidityDays(),
                request.feeAmountXof());
        return ResponseEntity.created(URI.create("/api/v1/schooldocs/templates/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplateResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentTemplateResponse>> listTemplates(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = SchoolDocsPagination.of(
                page, size, sortBy, direction, SchoolDocsPagination.TEMPLATE_SORT_FIELDS, "code", Sort.Direction.ASC);
        Page<DocumentTemplateResponse> result = templateService
                .listTemplates(documentType, active, search, pageable)
                .map(DocumentTemplateResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(result));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<DocumentTemplateResponse> getTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplateResponse.from(templateService.getTemplate(templateId)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<DocumentTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateDocumentTemplateRequest request) {
        DocumentTemplate updated = templateService.updateTemplate(
                templateId,
                request.name(),
                request.description(),
                request.documentType(),
                request.language(),
                request.titleTemplate(),
                request.bodyTemplate(),
                request.footerTemplate(),
                request.defaultValidityDays(),
                request.feeAmountXof());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplateResponse.from(updated));
    }

    @PostMapping("/{templateId}/activate")
    public ResponseEntity<DocumentTemplateResponse> activateTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplateResponse.from(templateService.activateTemplate(templateId)));
    }

    @PostMapping("/{templateId}/deactivate")
    public ResponseEntity<DocumentTemplateResponse> deactivateTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplateResponse.from(templateService.deactivateTemplate(templateId)));
    }

    @PostMapping("/{templateId}/preview")
    public ResponseEntity<DocumentTemplatePreviewResponse> previewTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody(required = false) PreviewDocumentTemplateRequest request) {
        Map<String, String> variables = request != null && request.variables() != null ? request.variables() : Map.of();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(DocumentTemplatePreviewResponse.from(templateService.previewTemplate(templateId, variables)));
    }
}
