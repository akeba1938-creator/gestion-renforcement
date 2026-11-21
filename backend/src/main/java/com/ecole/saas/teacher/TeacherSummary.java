package com.ecole.saas.teacher;

import java.util.UUID;

/**
 * Vue immuable d'un dossier enseignant / intervenant exposée aux autres modules applicatifs.
 */
public record TeacherSummary(
        UUID id,
        String employeeCode,
        UUID userId,
        String firstName,
        String lastName,
        String teacherType,
        String phone,
        String email,
        Long hourlyRateXof,
        String status,
        boolean active) {
}
