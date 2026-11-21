package com.ecole.saas.student;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Frontière publique du module {@code student} exposée aux autres modules applicatifs
 * ({@code family}, {@code grade}, {@code attendance}, {@code finance}, {@code schoolimport}, {@code reporting})
 * pour consulter les élèves et inscriptions du tenant courant sans exposer les entités internes.
 */
public interface StudentDirectoryPort {

    Optional<StudentSummary> findStudentById(UUID studentId);

    Optional<StudentSummary> findStudentByMatricule(String matricule);

    List<StudentSummary> findStudentsByIds(Collection<UUID> studentIds);

    List<StudentSummary> findStudentsByMatricules(Collection<String> matricules);

    List<StudentSummary> findStudentsByLastNamesIgnoreCase(Collection<String> lastNames);

    Optional<StudentContactSummary> findStudentContactById(UUID studentId);

    Optional<StudentContactSummary> findStudentContactByUserId(UUID userId);

    List<StudentContactSummary> findStudentContactsByIds(Collection<UUID> studentIds);

    Optional<StudentEnrollmentSummary> findEnrollmentByStudentAndYear(UUID studentId, UUID academicYearId);

    List<StudentEnrollmentSummary> findActiveEnrollmentsByYearAndStudents(UUID academicYearId, Collection<UUID> studentIds);

    Optional<StudentEnrollmentSummary> findCurrentEnrollmentForStudent(UUID studentId);

    List<StudentEnrollmentSummary> findActiveEnrollmentsByClassRoom(UUID classRoomId);

    Map<UUID, Long> countActiveEnrollmentsByClassRooms(Collection<UUID> classRoomIds);

    Set<UUID> findActiveStudentIdsByClassRooms(Collection<UUID> classRoomIds);

    boolean isStudentActivelyEnrolledInAnyClass(UUID studentId, Collection<UUID> classRoomIds);

    long countActiveStudents();
}
