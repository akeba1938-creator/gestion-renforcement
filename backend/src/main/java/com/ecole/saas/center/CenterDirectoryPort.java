package com.ecole.saas.center;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code center} (mode centre de renforcement /
 * soutien scolaire) exposée aux autres modules applicatifs ({@code reporting}, {@code communication},
 * {@code schooldocs}) sans exposer les entités internes ni les tables du module.
 */
public interface CenterDirectoryPort {

    Optional<CenterGroupSummary> findGroupById(UUID groupId);

    List<CenterGroupSummary> findActiveGroupsByAcademicYear(UUID academicYearId);

    List<CenterGroupEnrollmentSummary> findActiveEnrollmentsByStudent(UUID studentId);

    List<CenterGroupEnrollmentSummary> findActiveEnrollmentsByGroup(UUID groupId);

    Optional<CenterSessionPackSummary> findPackById(UUID packId);

    Set<UUID> findAssignedGroupIdsByTeacherId(UUID teacherId);

    boolean isTeacherAssignedToGroup(UUID teacherId, UUID groupId);

    StudentCenterSummary getStudentCenterSummary(UUID studentId, LocalDate fromDate, LocalDate toDate);

    TutorCenterWorkloadSummary getTutorWorkloadSummary(UUID teacherId, LocalDate fromDate, LocalDate toDate);
}
