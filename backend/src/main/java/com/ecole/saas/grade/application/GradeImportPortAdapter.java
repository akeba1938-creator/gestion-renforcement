package com.ecole.saas.grade.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.EvaluationPeriodSummary;
import com.ecole.saas.grade.EvaluationImportContext;
import com.ecole.saas.grade.GradeImportPort;
import com.ecole.saas.grade.ImportGradeEntryCommand;
import com.ecole.saas.grade.domain.Evaluation;
import com.ecole.saas.grade.domain.GradeAttendanceStatus;
import com.ecole.saas.grade.domain.StudentGrade;
import com.ecole.saas.grade.infrastructure.EvaluationRepository;
import com.ecole.saas.grade.infrastructure.StudentGradeRepository;
import com.ecole.saas.shared.domain.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptateur applicatif de {@link GradeImportPort} déléguant à {@link EvaluationService}
 * pour préserver le blocage période fermée, la restriction enseignant, {@code saveAll} et l'audit.
 */
@Service
public class GradeImportPortAdapter implements GradeImportPort {

    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final AcademicStructurePort academicStructurePort;
    private final EvaluationService evaluationService;

    public GradeImportPortAdapter(
            EvaluationRepository evaluationRepository,
            StudentGradeRepository studentGradeRepository,
            AcademicStructurePort academicStructurePort,
            EvaluationService evaluationService) {
        this.evaluationRepository = evaluationRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.academicStructurePort = academicStructurePort;
        this.evaluationService = evaluationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationImportContext> findEvaluationImportContext(UUID evaluationId) {
        TenantContext.requireCurrent();
        if (evaluationId == null) {
            return Optional.empty();
        }
        return evaluationRepository.findById(evaluationId).map(evaluation -> {
            String periodStatus = academicStructurePort
                    .findEvaluationPeriodById(evaluation.getEvaluationPeriodId())
                    .map(EvaluationPeriodSummary::status)
                    .orElse("UNKNOWN");
            Set<UUID> existingStudents = studentGradeRepository
                    .findByEvaluationIdOrderByCreatedAtAsc(evaluationId)
                    .stream()
                    .map(StudentGrade::getStudentId)
                    .collect(Collectors.toUnmodifiableSet());
            return new EvaluationImportContext(
                    evaluation.getId(),
                    evaluation.getAcademicYearId(),
                    evaluation.getEvaluationPeriodId(),
                    periodStatus,
                    evaluation.getClassRoomId(),
                    evaluation.getSubjectId(),
                    evaluation.getTitle(),
                    evaluation.getStatus().name(),
                    evaluation.getMaxScore(),
                    existingStudents);
        });
    }

    @Override
    public int upsertGradesForImport(UUID evaluationId, List<ImportGradeEntryCommand> entries, UUID actorId) {
        List<EvaluationService.StudentGradeInput> inputs = entries.stream()
                .map(entry -> new EvaluationService.StudentGradeInput(
                        entry.studentId(),
                        GradeAttendanceStatus.valueOf(entry.attendanceStatus().trim().toUpperCase(Locale.ROOT)),
                        entry.score(),
                        entry.comment()))
                .toList();
        return evaluationService.upsertGrades(evaluationId, inputs, actorId).size();
    }
}
