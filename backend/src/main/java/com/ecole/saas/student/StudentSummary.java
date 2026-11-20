package com.ecole.saas.student;

import java.time.LocalDate;
import java.util.UUID;

public record StudentSummary(
        UUID id,
        String matricule,
        String firstName,
        String lastName,
        String gender,
        LocalDate birthDate,
        String status,
        boolean active) {
}
