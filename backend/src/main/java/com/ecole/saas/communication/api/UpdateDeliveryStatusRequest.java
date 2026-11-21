package com.ecole.saas.communication.api;

import com.ecole.saas.communication.DeliveryStatus;
import com.ecole.saas.communication.domain.DeliveryFailureCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateDeliveryStatusRequest(
        @NotNull(message = "Le nouveau statut de remise est obligatoire.")
        DeliveryStatus status,
        @Size(max = 60, message = "Le statut fournisseur ne peut pas dépasser 60 caractères.")
        String providerStatus,
        DeliveryFailureCode failureCode,
        @Size(max = 500, message = "Le motif d'échec ne peut pas dépasser 500 caractères.")
        String failureReason) {
}
