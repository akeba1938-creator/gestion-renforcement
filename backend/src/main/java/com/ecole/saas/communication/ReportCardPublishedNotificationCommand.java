package com.ecole.saas.communication;

import java.util.Objects;
import java.util.UUID;

/**
 * Commande typée pour notifier la famille lors de la publication d'un bulletin de période.
 */
public record ReportCardPublishedNotificationCommand(
        String idempotencyKey,
        UUID studentId,
        UUID reportCardId,
        CommunicationChannel channel,
        String periodLabel,
        String className,
        String generalAverageDisplay,
        Integer classRank,
        String mention,
        String templateCode) {

    public ReportCardPublishedNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clé d'idempotence est obligatoire.");
        }
        Objects.requireNonNull(studentId, "L'identifiant élève est obligatoire.");
        Objects.requireNonNull(channel, "Le canal de diffusion est obligatoire.");
        if (periodLabel == null || periodLabel.isBlank()) {
            throw new IllegalArgumentException("Le libellé de période est obligatoire.");
        }
    }
}
