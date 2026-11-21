package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
import com.ecole.saas.communication.domain.MessageTemplate;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record MessageTemplateResponse(
        UUID id,
        String code,
        String name,
        String description,
        MessageCategory category,
        CommunicationChannel channel,
        String language,
        String subjectTemplate,
        String bodyTemplate,
        Set<String> variables,
        long estimatedUnitCostXof,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static MessageTemplateResponse from(MessageTemplate t) {
        return new MessageTemplateResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getCategory(),
                t.getChannel(),
                t.getLanguage(),
                t.getSubjectTemplate(),
                t.getBodyTemplate(),
                t.requiredVariables(),
                t.getEstimatedUnitCostXof(),
                t.isActive(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
