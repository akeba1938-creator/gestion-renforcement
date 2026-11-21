package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.application.MessageTemplateService;
import com.ecole.saas.communication.domain.MessageTemplate;
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
@RequestMapping("/api/v1/communication/templates")
public class MessageTemplateController {

    private final MessageTemplateService templateService;

    public MessageTemplateController(MessageTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public ResponseEntity<MessageTemplateResponse> createTemplate(
            @Valid @RequestBody CreateMessageTemplateRequest request) {
        MessageTemplate created = templateService.createTemplate(
                request.code(),
                request.name(),
                request.description(),
                request.category(),
                request.channel(),
                request.language(),
                request.subjectTemplate(),
                request.bodyTemplate(),
                request.estimatedUnitCostXof() != null ? request.estimatedUnitCostXof() : 0L);
        return ResponseEntity.created(URI.create("/api/v1/communication/templates/" + created.getId()))
                .cacheControl(CacheControl.noStore())
                .body(MessageTemplateResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<PageResponse<MessageTemplateResponse>> listTemplates(
            @RequestParam(required = false) CommunicationChannel channel,
            @RequestParam(required = false) MessageCategory category,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        Pageable pageable = CommunicationPagination.of(
                page,
                size,
                sortBy,
                direction,
                CommunicationPagination.TEMPLATE_SORT_FIELDS,
                "code",
                Sort.Direction.ASC);
        Page<MessageTemplateResponse> templates = templateService
                .listTemplates(channel, category, active, query, pageable)
                .map(MessageTemplateResponse::from);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PageResponse.from(templates));
    }

    @GetMapping("/{templateId}")
    public ResponseEntity<MessageTemplateResponse> getTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageTemplateResponse.from(templateService.getTemplate(templateId)));
    }

    @PutMapping("/{templateId}")
    public ResponseEntity<MessageTemplateResponse> updateTemplate(
            @PathVariable UUID templateId,
            @Valid @RequestBody UpdateMessageTemplateRequest request) {
        MessageTemplate updated = templateService.updateTemplate(
                templateId,
                request.name(),
                request.description(),
                request.category(),
                request.channel(),
                request.language(),
                request.subjectTemplate(),
                request.bodyTemplate(),
                request.estimatedUnitCostXof() != null ? request.estimatedUnitCostXof() : 0L);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageTemplateResponse.from(updated));
    }

    @PostMapping("/{templateId}/activate")
    public ResponseEntity<MessageTemplateResponse> activateTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageTemplateResponse.from(templateService.setActive(templateId, true)));
    }

    @PostMapping("/{templateId}/deactivate")
    public ResponseEntity<MessageTemplateResponse> deactivateTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(MessageTemplateResponse.from(templateService.setActive(templateId, false)));
    }

    @PostMapping("/{templateId}/preview")
    public ResponseEntity<TemplatePreviewResponse> previewTemplate(
            @PathVariable UUID templateId,
            @RequestBody(required = false) PreviewTemplateRequest request) {
        Map<String, String> variables = request != null && request.variables() != null ? request.variables() : Map.of();
        boolean strict = request == null || request.strict() == null || request.strict();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(TemplatePreviewResponse.from(templateService.previewTemplate(templateId, variables, strict)));
    }
}
