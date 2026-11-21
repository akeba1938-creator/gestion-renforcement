package com.ecole.saas.communication;

/**
 * Statuts du cycle de vie d'une campagne ou d'un envoi de communication.
 */
public enum CampaignStatus {
    DRAFT,
    SCHEDULED,
    QUEUED,
    SENDING,
    COMPLETED,
    PARTIALLY_FAILED,
    FAILED,
    CANCELLED
}
