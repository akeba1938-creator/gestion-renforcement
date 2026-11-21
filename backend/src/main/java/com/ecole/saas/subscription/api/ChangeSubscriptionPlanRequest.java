package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.BillingCycle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ChangeSubscriptionPlanRequest(
        @NotNull(message = "L'identifiant du nouveau forfait est obligatoire.")
        UUID planId,
        BillingCycle billingCycle,
        Boolean resetExtraQuotas,
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
