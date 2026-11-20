package com.ecole.saas.center.domain;

import java.time.LocalDate;

/**
 * Politique pure de consommation et de restitution des crédits de séances sur les packs élèves.
 */
public final class CenterPackConsumptionPolicy {

    private CenterPackConsumptionPolicy() {
    }

    /**
     * Applique la transition de consommation de pack lors d'un pointage ou de sa modification.
     *
     * @param attendance       le pointage déjà mis à jour avec le nouveau statut
     * @param previouslyConsumedPack le pack précédemment débité (si non nul)
     * @param eligiblePack     le pack éligible de l'élève/inscription (si non nul)
     * @param sessionDate      la date de la séance
     */
    public static void reconcileAttendancePack(
            CenterSessionAttendance attendance,
            CenterStudentPack previouslyConsumedPack,
            CenterStudentPack eligiblePack,
            LocalDate sessionDate) {
        boolean shouldConsume = attendance.getStatus().countsAsAttended();

        if (!shouldConsume) {
            if (previouslyConsumedPack != null) {
                previouslyConsumedPack.refundOneSession(sessionDate);
            }
            attendance.clearPackConsumed();
            return;
        }

        // L'élève est PRESENT ou LATE
        if (previouslyConsumedPack != null) {
            // Déjà consommé sur ce pointage : opération idempotente
            return;
        }

        if (eligiblePack == null) {
            return;
        }

        if (eligiblePack.getBillingMode() == CenterPackBillingMode.SESSION_PACK
                || eligiblePack.getTotalSessions() != null) {
            eligiblePack.consumeOneSession(sessionDate);
            attendance.markPackConsumed(eligiblePack.getId());
        } else {
            // Forfait illimité sur période (MONTHLY_FLAT / TERM_FLAT sans plafond de séances)
            if (eligiblePack.getStatus() == CenterStudentPackStatus.CANCELLED) {
                throw new CenterConflictException(
                        "center-pack-cancelled",
                        "Forfait annulé",
                        "Le forfait rattaché à l'élève a été annulé.");
            }
            if (!eligiblePack.isValidOnDate(sessionDate)) {
                throw new CenterConflictException(
                        "center-pack-expired",
                        "Forfait hors période de validité",
                        "Le forfait rattaché à l'élève n'est pas valide à la date de la séance (" + sessionDate + ").");
            }
        }
    }

    public static void refundOnSessionCancellation(
            CenterSessionAttendance attendance,
            CenterStudentPack consumedPack,
            LocalDate sessionDate) {
        if (consumedPack != null && attendance.getConsumedStudentPackId() != null) {
            consumedPack.refundOneSession(sessionDate);
            attendance.clearPackConsumed();
        }
    }
}
