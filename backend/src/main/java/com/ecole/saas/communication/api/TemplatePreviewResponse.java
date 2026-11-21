package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.application.MessageTemplateService;
import java.util.UUID;

public record TemplatePreviewResponse(
        UUID templateId,
        String code,
        CommunicationChannel channel,
        String renderedSubject,
        String renderedBody,
        int smsSegments,
        long estimatedCostXof) {

    public static TemplatePreviewResponse from(MessageTemplateService.TemplatePreview preview) {
        return new TemplatePreviewResponse(
                preview.templateId(),
                preview.code(),
                preview.channel(),
                preview.renderedSubject(),
                preview.renderedBody(),
                preview.smsSegments(),
                preview.estimatedCostXof());
    }
}
