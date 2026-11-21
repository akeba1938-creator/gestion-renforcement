package com.ecole.saas.attendance;

import java.util.UUID;

/**
 * Commande publique immuable d'un pointage élève issu d'un import massif.
 */
public record ImportAttendanceEntryCommand(
        UUID studentId,
        String status,
        Integer lateMinutes,
        String notes) {
}
