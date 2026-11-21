package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.domain.ConsentSource;
import com.ecole.saas.communication.domain.RecipientType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.UUID;

public record UpsertContactPreferenceRequest(
        @NotNull(message = "Le type de contact est obligatoire.")
        RecipientType contactType,
        @NotNull(message = "L'identifiant de contact est obligatoire.")
        UUID contactId,
        Boolean inAppEnabled,
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean emailEnabled,
        Boolean marketingOptIn,
        CommunicationChannel preferredChannel,
        @Size(max = 10, message = "La langue ne peut pas dépasser 10 caractères.")
        String preferredLanguage,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        ConsentSource consentSource) {
}
