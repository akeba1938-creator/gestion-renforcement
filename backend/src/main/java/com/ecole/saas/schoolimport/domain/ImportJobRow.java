package com.ecole.saas.schoolimport.domain;

import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;

/**
 * Détail ligne par ligne d'un lot d'import : résultat de l'analyse à sec, erreurs détaillées,
 * action planifiée puis résultat réel après exécution.
 */
@Entity
@Table(name = "schoolimport_job_rows")
public class ImportJobRow extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "natural_key", length = 200)
    private String naturalKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false, length = 25)
    private ImportRowStatus rowStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "planned_action", nullable = false, length = 25)
    private ImportRowAction plannedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "applied_action", length = 25)
    private ImportRowAction appliedAction;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "error_field", length = 80)
    private String errorField;

    @Column(name = "error_message", length = 600)
    private String errorMessage;

    @Column(name = "target_entity_type", length = 40)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;

    @Column(name = "row_preview_json", columnDefinition = "TEXT")
    private String rowPreviewJson;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ImportJobRow() {
    }

    public ImportJobRow(
            UUID jobId,
            int rowNumber,
            String naturalKey,
            ImportRowStatus rowStatus,
            ImportRowAction plannedAction,
            String errorCode,
            String errorField,
            String errorMessage,
            String targetEntityType,
            UUID targetEntityId,
            String rowPreviewJson) {
        this.jobId = Objects.requireNonNull(jobId, "L'identifiant du lot est obligatoire.");
        if (rowNumber < 2) {
            throw new SchoolImportValidationException("Le numéro de ligne doit être supérieur ou égal à 2.");
        }
        this.rowNumber = rowNumber;
        this.naturalKey = truncate(naturalKey, 200);
        this.rowStatus = Objects.requireNonNull(rowStatus, "Le statut de ligne est obligatoire.");
        this.plannedAction = Objects.requireNonNull(plannedAction, "L'action planifiée est obligatoire.");
        this.errorCode = truncate(errorCode, 60);
        this.errorField = truncate(errorField, 80);
        this.errorMessage = truncate(errorMessage, 600);
        this.targetEntityType = truncate(targetEntityType, 40);
        this.targetEntityId = targetEntityId;
        this.rowPreviewJson = rowPreviewJson;
    }

    public void markCreated(String entityType, UUID entityId, String updatedNaturalKey) {
        this.rowStatus = ImportRowStatus.CREATED;
        this.appliedAction = ImportRowAction.CREATE;
        this.targetEntityType = truncate(entityType, 40);
        this.targetEntityId = entityId;
        if (updatedNaturalKey != null && !updatedNaturalKey.isBlank()) {
            this.naturalKey = truncate(updatedNaturalKey, 200);
        }
        this.errorCode = null;
        this.errorField = null;
        this.errorMessage = null;
    }

    public void markUpdated(String entityType, UUID entityId, String updatedNaturalKey) {
        this.rowStatus = ImportRowStatus.UPDATED;
        this.appliedAction = ImportRowAction.UPDATE;
        this.targetEntityType = truncate(entityType, 40);
        this.targetEntityId = entityId;
        if (updatedNaturalKey != null && !updatedNaturalKey.isBlank()) {
            this.naturalKey = truncate(updatedNaturalKey, 200);
        }
        this.errorCode = null;
        this.errorField = null;
        this.errorMessage = null;
    }

    public void markSkipped(String entityType, UUID entityId) {
        this.rowStatus = ImportRowStatus.SKIPPED;
        this.appliedAction = ImportRowAction.SKIP_DUPLICATE;
        if (entityType != null) {
            this.targetEntityType = truncate(entityType, 40);
        }
        if (entityId != null) {
            this.targetEntityId = entityId;
        }
    }

    public void markExecutionError(String errorCode, String errorField, String errorMessage) {
        this.rowStatus = ImportRowStatus.ERROR;
        this.appliedAction = ImportRowAction.ERROR;
        this.errorCode = truncate(errorCode != null ? errorCode : "EXECUTION_ERROR", 60);
        this.errorField = truncate(errorField, 80);
        this.errorMessage = truncate(
                errorMessage != null && !errorMessage.isBlank()
                        ? errorMessage
                        : "Erreur lors de l'application de la ligne.",
                600);
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public ImportRowStatus getRowStatus() {
        return rowStatus;
    }

    public ImportRowAction getPlannedAction() {
        return plannedAction;
    }

    public ImportRowAction getAppliedAction() {
        return appliedAction;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorField() {
        return errorField;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getTargetEntityType() {
        return targetEntityType;
    }

    public UUID getTargetEntityId() {
        return targetEntityId;
    }

    public String getRowPreviewJson() {
        return rowPreviewJson;
    }

    public long getVersion() {
        return version;
    }
}
