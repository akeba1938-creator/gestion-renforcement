package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.BillingCycle;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateActiveSubscriptionRequest(
        @NotNull(message = "L'identifiant du forfait est obligatoire.")
        UUID planId,
        @NotNull(message = "Le cycle de facturation est obligatoire.")
        BillingCycle billingCycle,
        Instant startsAt,
        Instant endsAt) {
}
