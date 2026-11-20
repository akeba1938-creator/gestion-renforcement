package com.ecole.saas.finance.api;

import com.ecole.saas.finance.domain.PaymentMethod;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RecordPaymentRequest(
        @NotNull(message = "La facture est obligatoire.") UUID invoiceId,
        UUID paymentScheduleId,
        UUID payerGuardianId,
        @Min(value = 1, message = "Le montant encaissé en FCFA doit être strictement positif.") long amountXof,
        @NotNull(message = "Le mode de paiement est obligatoire.") PaymentMethod paymentMethod,
        @Size(max = 120, message = "La référence externe ne doit pas dépasser 120 caractères.")
                String externalReference,
        @Size(max = 128, message = "La clé d'idempotence ne doit pas dépasser 128 caractères.")
                String idempotencyKey,
        @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères.") String notes) {
}
