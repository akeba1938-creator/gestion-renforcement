package com.ecole.saas.academic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique du module {@code academic} exposée aux autres modules applicatifs
 * ({@code student}, {@code grade}, {@code attendance}, {@code finance}, {@code center})
 * pour consulter la structure académique du tenant courant sans exposer les entités internes.
 */
public interface AcademicStructurePort {

    Optional<AcademicYearSummary> findCurrentAcademicYear();

    Optional<AcademicYearSummary> findAcademicYearById(UUID academicYearId);

    List<EvaluationPeriodSummary> findEvaluationPeriodsByYear(UUID academicYearId);

    Optional<EvaluationPeriodSummary> findEvaluationPeriodById(UUID periodId);

    Optional<GradeLevelSummary> findGradeLevelById(UUID gradeLevelId);

    Optional<SubjectSummary> findSubjectById(UUID subjectId);

    Optional<ClassRoomSummary> findClassRoomById(UUID classRoomId);

    List<ClassSubjectSummary> findActiveSubjectsForClass(UUID classRoomId);
}
