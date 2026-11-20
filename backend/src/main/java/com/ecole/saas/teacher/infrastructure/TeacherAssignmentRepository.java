package com.ecole.saas.teacher.infrastructure;

import com.ecole.saas.teacher.domain.TeacherAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TeacherAssignmentRepository
        extends JpaRepository<TeacherAssignment, UUID>, JpaSpecificationExecutor<TeacherAssignment> {

    boolean existsByTeacherIdAndClassRoomIdAndSubjectIdAndActiveTrue(
            UUID teacherId, UUID classRoomId, UUID subjectId);

    boolean existsByTeacherIdAndClassRoomIdAndActiveTrue(UUID teacherId, UUID classRoomId);

    boolean existsByTeacherIdAndClassRoomIdAndHomeroomTeacherTrueAndActiveTrue(UUID teacherId, UUID classRoomId);

    Optional<TeacherAssignment> findByClassRoomIdAndHomeroomTeacherTrueAndActiveTrue(UUID classRoomId);

    List<TeacherAssignment> findByTeacherIdAndActiveTrueOrderByCreatedAtAsc(UUID teacherId);

    List<TeacherAssignment> findByClassRoomIdAndActiveTrueOrderByCreatedAtAsc(UUID classRoomId);
}
