package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterGroupTutor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterGroupTutorRepository extends JpaRepository<CenterGroupTutor, UUID> {

    List<CenterGroupTutor> findByGroupIdOrderByActiveDescLeadTutorDescCreatedAtAsc(UUID groupId);

    List<CenterGroupTutor> findByGroupIdAndActiveTrue(UUID groupId);

    List<CenterGroupTutor> findByTeacherId(UUID teacherId);

    List<CenterGroupTutor> findByTeacherIdAndActiveTrue(UUID teacherId);

    List<CenterGroupTutor> findByGroupIdAndTeacherIdOrderByActiveDescUpdatedAtDesc(UUID groupId, UUID teacherId);

    Optional<CenterGroupTutor> findByGroupIdAndLeadTutorTrueAndActiveTrue(UUID groupId);

    boolean existsByGroupIdAndTeacherIdAndActiveTrue(UUID groupId, UUID teacherId);

    boolean existsByGroupIdAndTeacherIdAndSubjectIdIsNullAndActiveTrue(UUID groupId, UUID teacherId);

    boolean existsByGroupIdAndTeacherIdAndSubjectIdAndActiveTrue(UUID groupId, UUID teacherId, UUID subjectId);

    Optional<CenterGroupTutor> findByIdAndGroupId(UUID id, UUID groupId);
}
