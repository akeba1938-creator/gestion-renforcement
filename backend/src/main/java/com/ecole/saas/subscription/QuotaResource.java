package com.ecole.saas.subscription;

/**
 * Ressources soumises à un quota d'abonnement par établissement.
 */
public enum QuotaResource {
    STUDENTS("élèves"),
    USERS("utilisateurs"),
    STORAGE_MB("stockage (Mo)");

    private final String label;

    QuotaResource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
