package com.ecole.saas.reporting.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArchiveReportSnapshotRequest(
        @NotBlank(message = "Le motif d'archivage est obligatoire.")
        @Size(min = 5, max = 500, message = "Le motif d'archivage doit contenir entre 5 et 500 caractères.")
        String reason) {
}
