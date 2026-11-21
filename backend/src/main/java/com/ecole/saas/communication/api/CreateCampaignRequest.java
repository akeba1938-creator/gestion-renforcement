package com.ecole.saas.communication.api;

import com.ecole.saas.communication.AudienceType;
import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.GuardianTargetingMode;
import com.ecole.saas.communication.MessageCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateCampaignRequest(
        @Size(max = 128, message = "La clé d'idempotence ne peut pas dépasser 128 caractères.")
        String idempotencyKey,
        @Size(max = 150, message = "Le titre ne peut pas dépasser 150 caractères.")
        String title,
        @NotNull(message = "La catégorie est obligatoire.")
        MessageCategory category,
        CommunicationChannel channel,
        UUID templateId,
        @NotNull(message = "Le type d'audience est obligatoire.")
        AudienceType audienceType,
        UUID audienceTargetId,
        GuardianTargetingMode guardianTargetingMode,
        @Size(max = 255, message = "L'objet ne peut pas dépasser 255 caractères.")
        String subject,
        @Size(max = 4000, message = "Le contenu du message ne peut pas dépasser 4000 caractères.")
        String body,
        Map<String, String> variables,
        Boolean respectMarketingOptOut,
        Instant scheduledAt,
        Boolean dispatchImmediately) {
}
