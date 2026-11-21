package com.ecole.saas.grade;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code grade} exposée aux autres modules applicatifs
 * ({@code schooldocs}, {@code reporting}, {@code communication}) pour consulter les synthèses de
 * notes et bulletins de période du tenant courant sans exposer les entités internes.
 */
public interface GradeSummaryPort {

    Optional<StudentPeriodGradeSummary> findStudentPeriodSummary(UUID studentId, UUID evaluationPeriodId);

    List<StudentPeriodGradeSummary> findStudentYearSummaries(UUID studentId, UUID academicYearId);

    Optional<ClassPeriodGradeStats> findClassPeriodStats(UUID classRoomId, UUID evaluationPeriodId);

    GradeReportingOverview getPeriodReportingOverview(
            UUID academicYearId, UUID evaluationPeriodId, Collection<UUID> classRoomIds);
}
