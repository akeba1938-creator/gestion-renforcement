package com.ecole.saas.student;

import java.time.LocalDate;

/**
 * Commande publique immuable d'import (création ou mise à jour contrôlée) d'un dossier élève.
 */
public record ImportStudentCommand(
        String firstName,
        String lastName,
        String gender,
        LocalDate birthDate,
        String birthPlace,
        String nationality,
        String address,
        String phone,
        String email,
        String medicalNotes,
        LocalDate admissionDate) {
}
