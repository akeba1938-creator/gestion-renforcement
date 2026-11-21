package com.ecole.saas.grade;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commande publique immuable d'une note élève issue d'un import massif.
 */
public record ImportGradeEntryCommand(
        UUID studentId,
        String attendanceStatus,
        BigDecimal score,
        String comment) {
}
