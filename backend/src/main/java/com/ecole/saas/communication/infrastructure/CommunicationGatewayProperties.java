package com.ecole.saas.communication.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration des adaptateurs de passerelle du module {@code communication}.
 * Coûts unitaires strictement en entiers FCFA ({@code long}), aucun secret en dur.
 */
@ConfigurationProperties(prefix = "app.communication.gateways")
public record CommunicationGatewayProperties(
        SmsGatewayProperties sms,
        WhatsAppGatewayProperties whatsapp,
        EmailGatewayProperties email) {

    public CommunicationGatewayProperties {
        sms = sms != null ? sms : new SmsGatewayProperties(25L, "LOCAL_STUB_SMS");
        whatsapp = whatsapp != null ? whatsapp : new WhatsAppGatewayProperties(15L, "LOCAL_STUB_WHATSAPP");
        email = email != null ? email : new EmailGatewayProperties(0L, "LOCAL_STUB_EMAIL");
    }

    public record SmsGatewayProperties(long unitCostXof, String providerName) {
        public SmsGatewayProperties {
            if (unitCostXof < 0) {
                throw new IllegalArgumentException("Le coût unitaire SMS en FCFA ne peut pas être négatif.");
            }
            providerName = (providerName == null || providerName.isBlank()) ? "LOCAL_STUB_SMS" : providerName.trim();
        }
    }

    public record WhatsAppGatewayProperties(long unitCostXof, String providerName) {
        public WhatsAppGatewayProperties {
            if (unitCostXof < 0) {
                throw new IllegalArgumentException("Le coût unitaire WhatsApp en FCFA ne peut pas être négatif.");
            }
            providerName = (providerName == null || providerName.isBlank())
                    ? "LOCAL_STUB_WHATSAPP"
                    : providerName.trim();
        }
    }

    public record EmailGatewayProperties(long unitCostXof, String providerName) {
        public EmailGatewayProperties {
            if (unitCostXof < 0) {
                throw new IllegalArgumentException("Le coût unitaire e-mail en FCFA ne peut pas être négatif.");
            }
            providerName = (providerName == null || providerName.isBlank()) ? "LOCAL_STUB_EMAIL" : providerName.trim();
        }
    }
}
