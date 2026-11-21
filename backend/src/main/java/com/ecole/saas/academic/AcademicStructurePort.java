package com.ecole.saas.academic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    Map<UUID, AcademicYearSummary> findAcademicYearsByIds(Collection<UUID> academicYearIds);

    List<EvaluationPeriodSummary> findEvaluationPeriodsByYear(UUID academicYearId);

    Optional<EvaluationPeriodSummary> findEvaluationPeriodById(UUID periodId);

    Map<UUID, EvaluationPeriodSummary> findEvaluationPeriodsByIds(Collection<UUID> periodIds);

    Optional<GradeLevelSummary> findGradeLevelById(UUID gradeLevelId);

    Map<UUID, GradeLevelSummary> findGradeLevelsByIds(Collection<UUID> gradeLevelIds);

    Optional<SubjectSummary> findSubjectById(UUID subjectId);

    Map<UUID, SubjectSummary> findSubjectsByIds(Collection<UUID> subjectIds);

    Optional<ClassRoomSummary> findClassRoomById(UUID classRoomId);

    Map<UUID, ClassRoomSummary> findClassRoomsByIds(Collection<UUID> classRoomIds);

    List<ClassSubjectSummary> findActiveSubjectsForClass(UUID classRoomId);
}
