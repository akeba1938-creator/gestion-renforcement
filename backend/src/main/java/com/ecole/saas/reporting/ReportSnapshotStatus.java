package com.ecole.saas.reporting;

/**
 * Cycle de vie d'un snapshot de rapport (archivage logique uniquement, aucune suppression physique).
 */
public enum ReportSnapshotStatus {
    READY,
    ARCHIVED
}
