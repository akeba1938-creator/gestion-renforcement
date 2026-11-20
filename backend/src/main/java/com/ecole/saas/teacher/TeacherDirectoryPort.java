package com.ecole.saas.teacher;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Frontière publique Spring Modulith du module {@code teacher} exposée aux autres modules
 * ({@code student}, {@code grade}, {@code attendance}) pour consulter les dossiers enseignants
 * et appliquer la restriction fine d'un {@code TEACHER}/{@code TUTOR} à ses classes/matières affectées.
 */
public interface TeacherDirectoryPort {

    Optional<TeacherSummary> findTeacherById(UUID teacherId);

    Optional<TeacherSummary> findTeacherByUserId(UUID userId);

    List<TeacherAssignmentSummary> findActiveAssignmentsByTeacherId(UUID teacherId);

    List<TeacherAssignmentSummary> findActiveAssignmentsByUserId(UUID userId);

    List<TeacherAssignmentSummary> findActiveAssignmentsByClassRoom(UUID classRoomId);

    Set<UUID> findAssignedClassRoomIdsByUserId(UUID userId);

    boolean isUserAssignedToClass(UUID userId, UUID classRoomId);

    boolean isUserAssignedToClassSubject(UUID userId, UUID classRoomId, UUID subjectId);

    boolean isUserHomeroomTeacherOfClass(UUID userId, UUID classRoomId);
}
