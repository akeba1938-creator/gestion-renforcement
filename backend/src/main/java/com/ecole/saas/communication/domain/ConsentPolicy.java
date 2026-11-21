package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.CommunicationChannel;
import com.ecole.saas.communication.MessageCategory;
import java.util.Objects;
import java.util.UUID;

/**
 * Politique métier pure d'évaluation du consentement, des préférences de canal
 * et de la joignabilité d'un destinataire avant remise.
 */
public final class ConsentPolicy {

    private ConsentPolicy() {
    }

    public static ConsentDecision evaluate(
            CommunicationChannel channel,
            MessageCategory category,
            boolean respectMarketingOptOut,
            ContactPreference preference,
            boolean recipientActive,
            String phone,
            String email,
            UUID userId) {
        Objects.requireNonNull(channel, "Le canal est obligatoire.");
        Objects.requireNonNull(category, "La catégorie est obligatoire.");

        if (!recipientActive) {
            return ConsentDecision.blocked(
                    DeliveryFailureCode.RECIPIENT_INACTIVE,
                    "Le destinataire est inactif ou archivé.");
        }

        if (preference != null) {
            if (!preference.isChannelEnabled(channel)) {
                return ConsentDecision.blocked(
                        DeliveryFailureCode.CHANNEL_OPTED_OUT,
                        "Le destinataire a désactivé le canal " + channel + ".");
            }
            boolean applyMarketingOptOut = respectMarketingOptOut || !category.isMandatoryTransactional();
            if (applyMarketingOptOut && respectMarketingOptOut && !preference.isMarketingOptIn()) {
                return ConsentDecision.blocked(
                        DeliveryFailureCode.MARKETING_OPTED_OUT,
                        "Le destinataire a refusé les communications non obligatoires (opt-out marketing).");
            }
            if (category == MessageCategory.ANNOUNCEMENT && !preference.isMarketingOptIn()) {
                return ConsentDecision.blocked(
                        DeliveryFailureCode.MARKETING_OPTED_OUT,
                        "Le destinataire a refusé les annonces et communications promotionnelles.");
            }
        }

        String address = resolveAddress(channel, phone, email, userId);
        if (address == null) {
            return ConsentDecision.blocked(
                    DeliveryFailureCode.MISSING_CONTACT_ADDRESS,
                    "Aucune coordonnée valide n'est disponible pour le canal " + channel + ".");
        }
        return ConsentDecision.allowed(address);
    }

    private static String resolveAddress(CommunicationChannel channel, String phone, String email, UUID userId) {
        return switch (channel) {
            case IN_APP -> userId != null ? "in-app:" + userId : null;
            case SMS, WHATSAPP -> (phone != null && !phone.isBlank()) ? phone.trim() : null;
            case EMAIL -> (email != null && !email.isBlank()) ? email.trim() : null;
        };
    }

    public record ConsentDecision(
            boolean allowed,
            String resolvedAddress,
            DeliveryFailureCode failureCode,
            String failureReason) {

        public static ConsentDecision allowed(String address) {
            return new ConsentDecision(true, address, null, null);
        }

        public static ConsentDecision blocked(DeliveryFailureCode code, String reason) {
            return new ConsentDecision(false, null, code, reason);
        }
    }
}
