package com.ecole.saas.schoolimport.application;

import com.ecole.saas.attendance.AttendanceImportPort;
import com.ecole.saas.attendance.ImportAttendanceEntryCommand;
import com.ecole.saas.family.FamilyImportPort;
import com.ecole.saas.family.FamilySummary;
import com.ecole.saas.family.GuardianSummary;
import com.ecole.saas.family.ImportGuardianCommand;
import com.ecole.saas.family.ImportStudentGuardianLinkCommand;
import com.ecole.saas.grade.GradeImportPort;
import com.ecole.saas.grade.ImportGradeEntryCommand;
import com.ecole.saas.schoolimport.ImportJobStatus;
import com.ecole.saas.schoolimport.ImportRowAction;
import com.ecole.saas.schoolimport.ImportRowStatus;
import com.ecole.saas.schoolimport.ImportType;
import com.ecole.saas.schoolimport.ImportValidationMode;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.AnalysisResult;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.AnalyzedRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedAttendanceRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedEnrollmentRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedFamilyGuardianRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedGradeRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedStudentRow;
import com.ecole.saas.schoolimport.application.SchoolImportRowAnalyzer.PreparedTeacherRow;
import com.ecole.saas.shared.domain.BusinessException;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.shared.domain.TenantId;
import com.ecole.saas.student.ImportEnrollmentCommand;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentImportPort;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.teacher.TeacherImportPort;
import com.ecole.saas.teacher.TeacherSummary;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exécuteur contrôlé des lignes validées d'un lot d'import :
 * <ul>
 *   <li>{@link ImportValidationMode#STRICT_ALL_OR_NOTHING} : tout ou rien dans une transaction unique
 *       encadrée par {@link TenantContext#executeWithTenant} (rollback intégral à la moindre erreur) ;</li>
 *   <li>{@link ImportValidationMode#PARTIAL_SKIP_ERRORS} : transactions {@code REQUIRES_NEW} isolées
 *       encadrées par {@link TenantContext#executeWithTenant} afin de garantir {@code set_config} RLS
 *       sur chaque transaction sans jamais corrompre les lignes valides suivantes.</li>
 * </ul>
 */
@Component
class SchoolImportRowExecutor {

    record RowExecutionOutcome(
            int rowNumber,
            ImportRowStatus finalStatus,
            ImportRowAction appliedAction,
            String entityType,
            UUID entityId,
            String updatedNaturalKey,
            String errorCode,
            String errorField,
            String errorMessage) {

        static RowExecutionOutcome created(int rowNumber, String entityType, UUID entityId, String naturalKey) {
            return new RowExecutionOutcome(
                    rowNumber, ImportRowStatus.CREATED, ImportRowAction.CREATE,
                    entityType, entityId, naturalKey, null, null, null);
        }

        static RowExecutionOutcome updated(int rowNumber, String entityType, UUID entityId, String naturalKey) {
            return new RowExecutionOutcome(
                    rowNumber, ImportRowStatus.UPDATED, ImportRowAction.UPDATE,
                    entityType, entityId, naturalKey, null, null, null);
        }

        static RowExecutionOutcome skipped(int rowNumber, String entityType, UUID entityId) {
            return new RowExecutionOutcome(
                    rowNumber, ImportRowStatus.SKIPPED, ImportRowAction.SKIP_DUPLICATE,
                    entityType, entityId, null, null, null, null);
        }

        static RowExecutionOutcome error(
                int rowNumber, String errorCode, String errorField, String errorMessage) {
            return new RowExecutionOutcome(
                    rowNumber, ImportRowStatus.ERROR, ImportRowAction.ERROR,
                    null, null, null, errorCode, errorField, errorMessage);
        }
    }

    record BatchExecutionResult(
            ImportJobStatus finalStatus,
            Map<Integer, RowExecutionOutcome> outcomesByRowNumber,
            int validRows,
            int errorRows,
            int duplicateRows,
            int createdCount,
            int updatedCount,
            int skippedCount) {
    }

    private final StudentImportPort studentImportPort;
    private final FamilyImportPort familyImportPort;
    private final TeacherImportPort teacherImportPort;
    private final GradeImportPort gradeImportPort;
    private final AttendanceImportPort attendanceImportPort;
    private final TransactionTemplate requiresNewTxTemplate;

    SchoolImportRowExecutor(
            StudentImportPort studentImportPort,
            FamilyImportPort familyImportPort,
            TeacherImportPort teacherImportPort,
            GradeImportPort gradeImportPort,
            AttendanceImportPort attendanceImportPort,
            PlatformTransactionManager transactionManager) {
        this.studentImportPort = studentImportPort;
        this.familyImportPort = familyImportPort;
        this.teacherImportPort = teacherImportPort;
        this.gradeImportPort = gradeImportPort;
        this.attendanceImportPort = attendanceImportPort;
        this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    BatchExecutionResult execute(
            TenantId tenantId,
            ImportType importType,
            ImportValidationMode validationMode,
            AnalysisResult analysis,
            UUID actorId) {
        if (validationMode == ImportValidationMode.STRICT_ALL_OR_NOTHING && analysis.errorRows() > 0) {
            Map<Integer, RowExecutionOutcome> outcomes = new LinkedHashMap<>();
            for (AnalyzedRow row : analysis.rows()) {
                if (row.rowStatus() == ImportRowStatus.ERROR) {
                    outcomes.put(row.rowNumber(), RowExecutionOutcome.error(
                            row.rowNumber(), row.errorCode(), row.errorField(), row.errorMessage()));
                } else if (row.rowStatus() == ImportRowStatus.DUPLICATE) {
                    outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                            row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
                }
            }
            return new BatchExecutionResult(
                    ImportJobStatus.FAILED,
                    outcomes,
                    analysis.validRows(),
                    analysis.errorRows(),
                    analysis.duplicateRows(),
                    0,
                    0,
                    0);
        }

        if (validationMode == ImportValidationMode.STRICT_ALL_OR_NOTHING) {
            return executeStrictAllOrNothing(tenantId, importType, analysis, actorId);
        }
        return executePartialSkipErrors(tenantId, importType, analysis, actorId);
    }

    private BatchExecutionResult executeStrictAllOrNothing(
            TenantId tenantId,
            ImportType importType,
            AnalysisResult analysis,
            UUID actorId) {
        Map<Integer, RowExecutionOutcome> outcomes = new LinkedHashMap<>();
        try {
            TenantContext.executeWithTenant(tenantId, () -> requiresNewTxTemplate.execute(status -> {
                if (importType == ImportType.GRADES) {
                    applyGradesBatch(analysis, actorId, outcomes);
                } else if (importType == ImportType.ATTENDANCE) {
                    applyAttendanceBatch(analysis, actorId, outcomes);
                } else {
                    Map<String, UUID> createdFamiliesByCode = new HashMap<>();
                    for (AnalyzedRow row : analysis.rows()) {
                        if (row.plannedAction() == ImportRowAction.SKIP_DUPLICATE) {
                            outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                                    row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
                            continue;
                        }
                        try {
                            RowExecutionOutcome outcome = applySingleRow(row, createdFamiliesByCode);
                            outcomes.put(row.rowNumber(), outcome);
                        } catch (RuntimeException ex) {
                            status.setRollbackOnly();
                            throw new StrictRowAbortException(row.rowNumber(), ex);
                        }
                    }
                }
                return null;
            }));
        } catch (StrictRowAbortException abort) {
            Map<Integer, RowExecutionOutcome> failedOutcomes = new LinkedHashMap<>();
            failedOutcomes.put(abort.rowNumber, toErrorOutcome(abort.rowNumber, abort.cause));
            return new BatchExecutionResult(
                    ImportJobStatus.FAILED,
                    failedOutcomes,
                    Math.max(0, analysis.validRows() - 1),
                    analysis.errorRows() + 1,
                    analysis.duplicateRows(),
                    0,
                    0,
                    0);
        } catch (RuntimeException ex) {
            Map<Integer, RowExecutionOutcome> failedOutcomes = new LinkedHashMap<>();
            int firstValidRow = analysis.rows().stream()
                    .filter(r -> r.rowStatus() == ImportRowStatus.VALID)
                    .map(AnalyzedRow::rowNumber)
                    .findFirst()
                    .orElse(2);
            failedOutcomes.put(firstValidRow, toErrorOutcome(firstValidRow, ex));
            return new BatchExecutionResult(
                    ImportJobStatus.FAILED,
                    failedOutcomes,
                    0,
                    Math.max(1, analysis.errorRows() + 1),
                    analysis.duplicateRows(),
                    0,
                    0,
                    0);
        }

        return summarizeResult(analysis, outcomes);
    }

    private BatchExecutionResult executePartialSkipErrors(
            TenantId tenantId,
            ImportType importType,
            AnalysisResult analysis,
            UUID actorId) {
        Map<Integer, RowExecutionOutcome> outcomes = new LinkedHashMap<>();

        if (importType == ImportType.GRADES || importType == ImportType.ATTENDANCE) {
            for (AnalyzedRow row : analysis.rows()) {
                if (row.rowStatus() == ImportRowStatus.ERROR) {
                    outcomes.put(row.rowNumber(), RowExecutionOutcome.error(
                            row.rowNumber(), row.errorCode(), row.errorField(), row.errorMessage()));
                } else if (row.plannedAction() == ImportRowAction.SKIP_DUPLICATE) {
                    outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                            row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
                }
            }
            boolean hasValidRows = analysis.rows().stream().anyMatch(r -> r.rowStatus() == ImportRowStatus.VALID);
            if (hasValidRows) {
                try {
                    TenantContext.executeWithTenant(tenantId, () -> requiresNewTxTemplate.execute(status -> {
                        if (importType == ImportType.GRADES) {
                            applyGradesBatch(analysis, actorId, outcomes);
                        } else {
                            applyAttendanceBatch(analysis, actorId, outcomes);
                        }
                        return null;
                    }));
                } catch (RuntimeException ex) {
                    for (AnalyzedRow row : analysis.rows()) {
                        if (row.rowStatus() == ImportRowStatus.VALID) {
                            outcomes.put(row.rowNumber(), toErrorOutcome(row.rowNumber(), ex));
                        }
                    }
                }
            }
            return summarizeResult(analysis, outcomes);
        }

        Map<String, UUID> createdFamiliesByCode = new HashMap<>();
        for (AnalyzedRow row : analysis.rows()) {
            if (row.rowStatus() == ImportRowStatus.ERROR) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.error(
                        row.rowNumber(), row.errorCode(), row.errorField(), row.errorMessage()));
                continue;
            }
            if (row.plannedAction() == ImportRowAction.SKIP_DUPLICATE) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                        row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
                continue;
            }
            try {
                RowExecutionOutcome outcome = TenantContext.executeWithTenant(
                        tenantId,
                        () -> requiresNewTxTemplate.execute(status -> applySingleRow(row, createdFamiliesByCode)));
                outcomes.put(row.rowNumber(), outcome);
            } catch (RuntimeException ex) {
                outcomes.put(row.rowNumber(), toErrorOutcome(row.rowNumber(), ex));
            }
        }
        return summarizeResult(analysis, outcomes);
    }

    private RowExecutionOutcome applySingleRow(AnalyzedRow row, Map<String, UUID> createdFamiliesByCode) {
        return switch (row.payload()) {
            case PreparedStudentRow studentRow -> applyStudentRow(row, studentRow);
            case PreparedFamilyGuardianRow famRow -> applyFamilyGuardianRow(row, famRow, createdFamiliesByCode);
            case PreparedTeacherRow teacherRow -> applyTeacherRow(row, teacherRow);
            case PreparedEnrollmentRow enrollmentRow -> applyEnrollmentRow(row, enrollmentRow);
            default -> throw new IllegalStateException("Type de ligne non pris en charge en exécution unitaire.");
        };
    }

    private RowExecutionOutcome applyStudentRow(AnalyzedRow row, PreparedStudentRow payload) {
        if (row.plannedAction() == ImportRowAction.UPDATE && payload.existingStudentId() != null) {
            StudentSummary updated = studentImportPort.updateStudentForImport(
                    payload.existingStudentId(), payload.command());
            return RowExecutionOutcome.updated(row.rowNumber(), "STUDENT", updated.id(), updated.matricule());
        }
        StudentSummary created = studentImportPort.createStudentForImport(payload.command());
        if (payload.optionalClassRoomId() != null && payload.academicYearId() != null) {
            studentImportPort.enrollStudentForImport(new ImportEnrollmentCommand(
                    created.id(),
                    payload.academicYearId(),
                    payload.optionalClassRoomId(),
                    "NEW_ADMISSION",
                    payload.command().admissionDate(),
                    null,
                    Boolean.FALSE,
                    "Inscription créée lors de l'import massif."));
        }
        return RowExecutionOutcome.created(row.rowNumber(), "STUDENT", created.id(), created.matricule());
    }

    private RowExecutionOutcome applyFamilyGuardianRow(
            AnalyzedRow row, PreparedFamilyGuardianRow payload, Map<String, UUID> createdFamiliesByCode) {
        UUID familyId = payload.existingFamilyId();
        if (payload.familyCommand() != null) {
            String famCodeKey = payload.familyCommand().code() != null
                    ? payload.familyCommand().code().trim().toUpperCase(Locale.ROOT)
                    : null;
            if (familyId == null && famCodeKey != null) {
                familyId = createdFamiliesByCode.get(famCodeKey);
            }
            if (familyId == null) {
                FamilySummary createdFam = familyImportPort.createFamilyForImport(payload.familyCommand());
                familyId = createdFam.id();
                if (famCodeKey != null) {
                    createdFamiliesByCode.put(famCodeKey, familyId);
                }
            } else if (row.plannedAction() == ImportRowAction.UPDATE) {
                familyImportPort.updateFamilyForImport(familyId, payload.familyCommand());
            }
        }

        ImportGuardianCommand resolvedGuardianCmd = new ImportGuardianCommand(
                familyId,
                payload.guardianCommand().firstName(),
                payload.guardianCommand().lastName(),
                payload.guardianCommand().gender(),
                payload.guardianCommand().phone(),
                payload.guardianCommand().secondaryPhone(),
                payload.guardianCommand().email(),
                payload.guardianCommand().profession(),
                payload.guardianCommand().address(),
                payload.guardianCommand().nationalIdNumber(),
                payload.guardianCommand().preferredLanguage());

        GuardianSummary savedGuardian;
        boolean updated = row.plannedAction() == ImportRowAction.UPDATE && payload.existingGuardianId() != null;
        if (updated) {
            savedGuardian = familyImportPort.updateGuardianForImport(
                    payload.existingGuardianId(), resolvedGuardianCmd);
        } else {
            savedGuardian = familyImportPort.createGuardianForImport(resolvedGuardianCmd);
        }

        if (payload.linkCommand() != null && payload.studentId() != null) {
            familyImportPort.linkOrUpdateGuardianForImport(new ImportStudentGuardianLinkCommand(
                    payload.studentId(),
                    savedGuardian.id(),
                    payload.linkCommand().relationshipType(),
                    payload.linkCommand().primaryContact(),
                    payload.linkCommand().financialResponsible(),
                    payload.linkCommand().legalCustody(),
                    payload.linkCommand().emergencyContact(),
                    payload.linkCommand().authorizedPickup(),
                    payload.linkCommand().notes()));
        }

        return updated
                ? RowExecutionOutcome.updated(row.rowNumber(), "GUARDIAN", savedGuardian.id(), row.naturalKey())
                : RowExecutionOutcome.created(row.rowNumber(), "GUARDIAN", savedGuardian.id(), row.naturalKey());
    }

    private RowExecutionOutcome applyTeacherRow(AnalyzedRow row, PreparedTeacherRow payload) {
        if (row.plannedAction() == ImportRowAction.UPDATE && payload.existingTeacherId() != null) {
            TeacherSummary updated = teacherImportPort.updateTeacherForImport(
                    payload.existingTeacherId(), payload.command());
            return RowExecutionOutcome.updated(row.rowNumber(), "TEACHER", updated.id(), updated.employeeCode());
        }
        TeacherSummary created = teacherImportPort.createTeacherForImport(payload.command());
        return RowExecutionOutcome.created(row.rowNumber(), "TEACHER", created.id(), created.employeeCode());
    }

    private RowExecutionOutcome applyEnrollmentRow(AnalyzedRow row, PreparedEnrollmentRow payload) {
        if (row.plannedAction() == ImportRowAction.UPDATE && payload.existingEnrollmentId() != null) {
            StudentEnrollmentSummary transferred = studentImportPort.transferClassForImport(
                    payload.existingEnrollmentId(),
                    payload.command().classRoomId(),
                    "Changement de classe contrôlé par import massif.");
            return RowExecutionOutcome.updated(
                    row.rowNumber(), "STUDENT_ENROLLMENT", transferred.id(), row.naturalKey());
        }
        StudentEnrollmentSummary created = studentImportPort.enrollStudentForImport(payload.command());
        return RowExecutionOutcome.created(row.rowNumber(), "STUDENT_ENROLLMENT", created.id(), row.naturalKey());
    }

    private void applyGradesBatch(
            AnalysisResult analysis,
            UUID actorId,
            Map<Integer, RowExecutionOutcome> outcomes) {
        List<AnalyzedRow> validRows = new ArrayList<>();
        List<ImportGradeEntryCommand> entries = new ArrayList<>();
        UUID evaluationId = analysis.resolvedEvaluationId();

        for (AnalyzedRow row : analysis.rows()) {
            if (row.plannedAction() == ImportRowAction.SKIP_DUPLICATE) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                        row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
            } else if (row.rowStatus() == ImportRowStatus.VALID && row.payload() instanceof PreparedGradeRow gradeRow) {
                validRows.add(row);
                entries.add(gradeRow.command());
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        gradeImportPort.upsertGradesForImport(evaluationId, entries, actorId);
        for (AnalyzedRow row : validRows) {
            if (row.plannedAction() == ImportRowAction.UPDATE) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.updated(
                        row.rowNumber(), "STUDENT_GRADE", row.targetEntityId(), row.naturalKey()));
            } else {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.created(
                        row.rowNumber(), "STUDENT_GRADE", row.targetEntityId(), row.naturalKey()));
            }
        }
    }

    private void applyAttendanceBatch(
            AnalysisResult analysis,
            UUID actorId,
            Map<Integer, RowExecutionOutcome> outcomes) {
        List<AnalyzedRow> validRows = new ArrayList<>();
        List<ImportAttendanceEntryCommand> entries = new ArrayList<>();
        UUID sessionId = analysis.resolvedAttendanceSessionId();

        for (AnalyzedRow row : analysis.rows()) {
            if (row.plannedAction() == ImportRowAction.SKIP_DUPLICATE) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.skipped(
                        row.rowNumber(), row.targetEntityType(), row.targetEntityId()));
            } else if (row.rowStatus() == ImportRowStatus.VALID
                    && row.payload() instanceof PreparedAttendanceRow attRow) {
                validRows.add(row);
                entries.add(attRow.command());
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        attendanceImportPort.upsertAttendanceForImport(sessionId, entries, actorId);
        for (AnalyzedRow row : validRows) {
            if (row.plannedAction() == ImportRowAction.UPDATE) {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.updated(
                        row.rowNumber(), "ATTENDANCE_RECORD", row.targetEntityId(), row.naturalKey()));
            } else {
                outcomes.put(row.rowNumber(), RowExecutionOutcome.created(
                        row.rowNumber(), "ATTENDANCE_RECORD", row.targetEntityId(), row.naturalKey()));
            }
        }
    }

    private static BatchExecutionResult summarizeResult(
            AnalysisResult analysis,
            Map<Integer, RowExecutionOutcome> outcomes) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        for (RowExecutionOutcome o : outcomes.values()) {
            switch (o.finalStatus()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case SKIPPED -> skipped++;
                case ERROR -> errors++;
                default -> {
                }
            }
        }
        ImportJobStatus finalStatus;
        if (errors == 0) {
            finalStatus = ImportJobStatus.COMPLETED;
        } else if (created > 0 || updated > 0 || skipped > 0) {
            finalStatus = ImportJobStatus.COMPLETED_WITH_ERRORS;
        } else {
            finalStatus = ImportJobStatus.FAILED;
        }
        return new BatchExecutionResult(
                finalStatus,
                outcomes,
                created + updated,
                errors,
                skipped,
                created,
                updated,
                skipped);
    }

    private static RowExecutionOutcome toErrorOutcome(int rowNumber, RuntimeException ex) {
        if (ex instanceof BusinessException bex) {
            return RowExecutionOutcome.error(
                    rowNumber,
                    bex.getProblemCode().toUpperCase(Locale.ROOT).replace('-', '_'),
                    null,
                    bex.getMessage());
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return RowExecutionOutcome.error(rowNumber, "BUSINESS_RULE_VIOLATION", null, ex.getMessage());
        }
        if (ex instanceof DataAccessException) {
            return RowExecutionOutcome.error(
                    rowNumber,
                    "DATA_INTEGRITY_CONFLICT",
                    null,
                    "Conflit d'intégrité des données détecté lors de l'enregistrement de la ligne.");
        }
        return RowExecutionOutcome.error(
                rowNumber,
                "EXECUTION_ERROR",
                null,
                "Erreur technique lors de l'application de la ligne.");
    }

    private static final class StrictRowAbortException extends RuntimeException {
        private final int rowNumber;
        private final RuntimeException cause;

        StrictRowAbortException(int rowNumber, RuntimeException cause) {
            super(cause);
            this.rowNumber = rowNumber;
            this.cause = cause;
        }
    }
}
