package com.ecole.saas.communication.domain;

import com.ecole.saas.communication.DeliveryStatus;
import java.util.Objects;

/**
 * Résultat normalisé retourné par un adaptateur de passerelle {@link ChannelDeliveryGateway}.
 */
public record GatewayDispatchResult(
        DeliveryStatus status,
        String providerName,
        String providerMessageId,
        String providerStatus,
        DeliveryFailureCode failureCode,
        String failureReason,
        long chargedCostXof) {

    public GatewayDispatchResult {
        Objects.requireNonNull(status, "Le statut retourné par la passerelle est obligatoire.");
        if (chargedCostXof < 0) {
            throw new CommunicationValidationException("Le coût facturé par la passerelle ne peut pas être négatif.");
        }
    }

    public static GatewayDispatchResult sent(
            String providerName,
            String providerMessageId,
            long chargedCostXof) {
        return new GatewayDispatchResult(
                DeliveryStatus.SENT,
                providerName,
                providerMessageId,
                "ACCEPTED",
                null,
                null,
                chargedCostXof);
    }

    public static GatewayDispatchResult delivered(
            String providerName,
            String providerMessageId,
            long chargedCostXof) {
        return new GatewayDispatchResult(
                DeliveryStatus.DELIVERED,
                providerName,
                providerMessageId,
                "DELIVERED",
                null,
                null,
                chargedCostXof);
    }

    public static GatewayDispatchResult failed(
            String providerName,
            DeliveryFailureCode failureCode,
            String failureReason) {
        return new GatewayDispatchResult(
                DeliveryStatus.FAILED,
                providerName,
                null,
                "FAILED",
                Objects.requireNonNull(failureCode, "Le code d'échec est obligatoire."),
                failureReason,
                0L);
    }
}
