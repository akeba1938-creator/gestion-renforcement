package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateMyPreferenceRequest(
        Boolean inAppEnabled,
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean emailEnabled,
        Boolean marketingOptIn,
        CommunicationChannel preferredChannel,
        @Size(max = 10, message = "La langue ne peut pas dépasser 10 caractères.")
        String preferredLanguage,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {
}
