package com.ecole.saas.communication;

import java.util.Objects;
import java.util.UUID;

/**
 * Commande typée pour notifier un responsable financier suite à l'émission d'un reçu de paiement
 * (montants strictement en entiers FCFA).
 */
public record PaymentReceiptNotificationCommand(
        String idempotencyKey,
        UUID studentId,
        UUID paymentId,
        CommunicationChannel channel,
        String receiptNumber,
        long amountXof,
        long remainingBalanceXof,
        String paymentMethod,
        String templateCode) {

    public PaymentReceiptNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clé d'idempotence est obligatoire.");
        }
        Objects.requireNonNull(studentId, "L'identifiant élève est obligatoire.");
        Objects.requireNonNull(channel, "Le canal de diffusion est obligatoire.");
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Le numéro de reçu est obligatoire.");
        }
        if (amountXof <= 0) {
            throw new IllegalArgumentException("Le montant payé en FCFA doit être strictement positif.");
        }
        if (remainingBalanceXof < 0) {
            throw new IllegalArgumentException("Le solde restant en FCFA ne peut pas être négatif.");
        }
    }
}
