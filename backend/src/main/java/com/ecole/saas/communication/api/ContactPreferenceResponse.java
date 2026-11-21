package com.ecole.saas.communication.api;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.application.ContactPreferenceService;
import com.ecole.saas.communication.domain.ConsentSource;
import com.ecole.saas.communication.domain.RecipientType;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ContactPreferenceResponse(
        UUID id,
        RecipientType contactType,
        UUID contactId,
        boolean inAppEnabled,
        boolean smsEnabled,
        boolean whatsappEnabled,
        boolean emailEnabled,
        boolean marketingOptIn,
        CommunicationChannel preferredChannel,
        String preferredLanguage,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        ConsentSource consentSource,
        Instant consentRecordedAt,
        boolean customized) {

    public static ContactPreferenceResponse from(ContactPreferenceService.ContactPreferenceView view) {
        return new ContactPreferenceResponse(
                view.id(),
                view.contactType(),
                view.contactId(),
                view.inAppEnabled(),
                view.smsEnabled(),
                view.whatsappEnabled(),
                view.emailEnabled(),
                view.marketingOptIn(),
                view.preferredChannel(),
                view.preferredLanguage(),
                view.quietHoursStart(),
                view.quietHoursEnd(),
                view.consentSource(),
                view.consentRecordedAt(),
                view.customized());
    }
}
