package com.ecole.saas.communication;

/**
 * Stratégie de sélection des tuteurs légaux lorsqu'une audience vise les familles d'élèves.
 */
public enum GuardianTargetingMode {
    ALL_ACTIVE,
    PRIMARY_ONLY,
    FINANCIAL_RESPONSIBLE_ONLY
}
