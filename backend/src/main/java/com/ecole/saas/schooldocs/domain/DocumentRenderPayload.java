package com.ecole.saas.schooldocs.domain;

import com.ecole.saas.schooldocs.DocumentType;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Données immuables transmises au moteur de génération binaire PDF officiel.
 */
public record DocumentRenderPayload(
        String schoolName,
        String schoolCode,
        String documentNumber,
        DocumentType documentType,
        boolean duplicate,
        int editionNumber,
        String reissueReason,
        String title,
        String body,
        String footer,
        String studentFullName,
        String studentMatricule,
        String academicYearLabel,
        String classRoomName,
        LocalDate issueDate,
        LocalDate validUntilDate,
        String verificationToken) {

    public DocumentRenderPayload {
        Objects.requireNonNull(schoolName, "Le nom de l'établissement est obligatoire.");
        Objects.requireNonNull(schoolCode, "Le code de l'établissement est obligatoire.");
        Objects.requireNonNull(documentNumber, "Le numéro officiel du document est obligatoire.");
        Objects.requireNonNull(documentType, "Le type de document est obligatoire.");
        Objects.requireNonNull(title, "Le titre du document est obligatoire.");
        Objects.requireNonNull(body, "Le contenu du document est obligatoire.");
        Objects.requireNonNull(issueDate, "La date d'émission est obligatoire.");
        Objects.requireNonNull(verificationToken, "Le jeton opaque de vérification est obligatoire.");
    }
}
