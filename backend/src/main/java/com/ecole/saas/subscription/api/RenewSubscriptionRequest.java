package com.ecole.saas.subscription.api;

import com.ecole.saas.subscription.domain.BillingCycle;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record RenewSubscriptionRequest(
        BillingCycle billingCycle,
        Instant endsAt,
        @Size(max = 255, message = "Le motif ne peut pas dépasser 255 caractères.")
        String reason) {
}
