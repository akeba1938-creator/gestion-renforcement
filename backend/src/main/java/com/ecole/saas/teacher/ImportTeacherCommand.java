package com.ecole.saas.teacher;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Commande publique immuable d'import (création ou mise à jour contrôlée) d'un dossier enseignant/intervenant.
 */
public record ImportTeacherCommand(
        String employeeCode,
        String firstName,
        String lastName,
        String gender,
        String teacherType,
        String phone,
        String secondaryPhone,
        String email,
        String address,
        String nationalIdNumber,
        LocalDate hireDate,
        Long hourlyRateXof,
        UUID primarySubjectId,
        String notes) {
}
