package com.ecole.saas.communication;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Commande typée pour notifier les tuteurs d'un élève suite à une absence ou un retard.
 */
public record AbsenceNotificationCommand(
        String idempotencyKey,
        UUID studentId,
        UUID attendanceRecordId,
        CommunicationChannel channel,
        LocalDate sessionDate,
        String attendanceStatus,
        Integer lateMinutes,
        String className,
        String templateCode) {

    public AbsenceNotificationCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("La clé d'idempotence est obligatoire.");
        }
        Objects.requireNonNull(studentId, "L'identifiant élève est obligatoire.");
        Objects.requireNonNull(channel, "Le canal de diffusion est obligatoire.");
        Objects.requireNonNull(sessionDate, "La date de séance est obligatoire.");
        if (attendanceStatus == null || attendanceStatus.isBlank()) {
            throw new IllegalArgumentException("Le statut d'assiduité est obligatoire.");
        }
    }
}
