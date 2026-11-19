package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.BillingCycle;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartTrialSubscriptionRequest(
        @NotNull(message = "L'identifiant du forfait est obligatoire.")
        UUID planId,
        BillingCycle targetBillingCycle,
        @Min(value = 1, message = "La durée d'essai doit être d'au moins 1 jour.")
        @Max(value = 365, message = "La durée d'essai ne peut pas dépasser 365 jours.")
        Integer trialDays) {
}
