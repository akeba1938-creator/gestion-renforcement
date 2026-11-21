package com.ecole.saas.communication;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande typée pour envoyer un rappel d'échéance de paiement impayée (montant en entiers FCFA).
 */
public record PaymentDueReminderNotificationCommand(
        String idempotencyKey,
        UUID studentId,
        UUID invoiceId,
        CommunicationChannel channel,
        String invoiceNumber,
        long amountDueXof,
        LocalDate dueDate,
        String templateCode) {

    public PaymentDueReminderNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clé d'idempotence est obligatoire.");
        }
        Objects.requireNonNull(studentId, "L'identifiant élève est obligatoire.");
        Objects.requireNonNull(channel, "Le canal de diffusion est obligatoire.");
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Le numéro de facture est obligatoire.");
        }
        if (amountDueXof <= 0) {
            throw new IllegalArgumentException("Le montant dû en FCFA doit être strictement positif.");
        }
        Objects.requireNonNull(dueDate, "La date d'échéance est obligatoire.");
    }
}
