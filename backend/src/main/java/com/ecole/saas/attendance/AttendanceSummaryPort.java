package com.ecole.saas.attendance;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code attendance} exposée aux autres modules
 * ({@code schooldocs}, {@code reporting}, {@code communication}) pour consulter les synthèses
 * d'assiduité du tenant courant sans exposer les entités internes.
 */
public interface AttendanceSummaryPort {

    StudentAttendanceSummary getStudentPeriodSummary(UUID studentId, UUID evaluationPeriodId);

    StudentAttendanceSummary getStudentSummary(
            UUID studentId,
            UUID academicYearId,
            UUID evaluationPeriodId,
            LocalDate fromDate,
            LocalDate toDate);

    List<StudentAttendanceSummary> getClassPeriodSummaries(UUID classRoomId, UUID evaluationPeriodId);

    AttendanceReportingOverview getAttendanceReportingOverview(
            UUID academicYearId,
            UUID evaluationPeriodId,
            Collection<UUID> classRoomIds,
            LocalDate fromDate,
            LocalDate toDate);
}
