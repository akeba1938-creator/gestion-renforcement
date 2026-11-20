package com.ecole.saas.student;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Frontière publique du module {@code student} exposée aux autres modules applicatifs
 * ({@code family}, {@code grade}, {@code attendance}, {@code finance}, {@code reporting})
 * pour consulter les élèves et inscriptions du tenant courant sans exposer les entités internes.
 */
public interface StudentDirectoryPort {

    Optional<StudentSummary> findStudentById(UUID studentId);

    Optional<StudentSummary> findStudentByMatricule(String matricule);

    List<StudentSummary> findStudentsByIds(Collection<UUID> studentIds);

    Optional<StudentEnrollmentSummary> findEnrollmentByStudentAndYear(UUID studentId, UUID academicYearId);

    Optional<StudentEnrollmentSummary> findCurrentEnrollmentForStudent(UUID studentId);

    List<StudentEnrollmentSummary> findActiveEnrollmentsByClassRoom(UUID classRoomId);

    long countActiveStudents();
}
