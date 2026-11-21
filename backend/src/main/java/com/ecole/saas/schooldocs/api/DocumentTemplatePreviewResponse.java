package com.ecole.saas.schooldocs.api;

import com.ecole.saas.schooldocs.DocumentType;
import com.ecole.saas.schooldocs.application.DocumentTemplateService.TemplatePreviewResult;
import java.util.Set;
import java.util.UUID;

public record DocumentTemplatePreviewResponse(
        UUID templateId,
        String templateCode,
        DocumentType documentType,
        String renderedTitle,
        String renderedBody,
        String renderedFooter,
        Set<String> declaredVariables) {

    public static DocumentTemplatePreviewResponse from(TemplatePreviewResult result) {
        return new DocumentTemplatePreviewResponse(
                result.template().getId(),
                result.template().getCode(),
                result.template().getDocumentType(),
                result.renderedTitle(),
                result.renderedBody(),
                result.renderedFooter(),
                result.variables());
    }
}
