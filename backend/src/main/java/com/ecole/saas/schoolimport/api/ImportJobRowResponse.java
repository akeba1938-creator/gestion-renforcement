package com.ecole.saas.schoolimport.api;

import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.domain.ImportJobRow;
import java.util.UUID;

public record ImportJobRowResponse(
        UUID id,
        int rowNumber,
        String naturalKey,
        ImportRowStatus rowStatus,
        ImportRowAction plannedAction,
        ImportRowAction appliedAction,
        String errorCode,
        String errorField,
        String errorMessage,
        String targetEntityType,
        UUID targetEntityId,
        String rowPreviewJson) {

    public static ImportJobRowResponse from(ImportJobRow row) {
        return new ImportJobRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getNaturalKey(),
                row.getRowStatus(),
                row.getPlannedAction(),
                row.getAppliedAction(),
                row.getErrorCode(),
                row.getErrorField(),
                row.getErrorMessage(),
                row.getTargetEntityType(),
                row.getTargetEntityId(),
                row.getRowPreviewJson());
    }
}
