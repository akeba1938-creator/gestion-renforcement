package com.ecole.saas.schooldocs;

/**
 * Types officiels de documents scolaires et administratifs émis par un établissement.
 */
public enum DocumentType {
    SCHOOLING_CERTIFICATE("Certificat de scolarité"),
    ENROLLMENT_ATTESTATION("Attestation d'inscription"),
    ATTENDANCE_ATTESTATION("Attestation d'assiduité"),
    SUCCESS_ATTESTATION("Attestation de réussite"),
    SUMMONS("Convocation"),
    CUSTOM_ADMINISTRATIVE("Document administratif");

    private final String defaultFrenchLabel;

    DocumentType(String defaultFrenchLabel) {
        this.defaultFrenchLabel = defaultFrenchLabel;
    }

    public String defaultFrenchLabel() {
        return defaultFrenchLabel;
    }
}
