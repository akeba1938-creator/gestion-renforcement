package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.domain.DocumentTemplate;
import com.ecole.saas.schooldocs.domain.DocumentTemplateRenderer;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DocumentTemplateResponse(
        UUID id,
        String code,
        String name,
        String description,
        DocumentType documentType,
        String language,
        String titleTemplate,
        String bodyTemplate,
        String footerTemplate,
        Integer defaultValidityDays,
        long feeAmountXof,
        boolean active,
        Set<String> declaredVariables,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentTemplateResponse from(DocumentTemplate template) {
        return new DocumentTemplateResponse(
                template.getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getDocumentType(),
                template.getLanguage(),
                template.getTitleTemplate(),
                template.getBodyTemplate(),
                template.getFooterTemplate(),
                template.getDefaultValidityDays(),
                template.getFeeAmountXof(),
                template.isActive(),
                DocumentTemplateRenderer.extractVariables(
                        template.getTitleTemplate(),
                        template.getBodyTemplate(),
                        template.getFooterTemplate()),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
