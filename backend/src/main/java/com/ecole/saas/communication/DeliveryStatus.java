package com.ecole.saas.communication;

/**
 * Statuts du cycle de vie d'une remise individuelle de message.
 */
public enum DeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED,
    READ
}
