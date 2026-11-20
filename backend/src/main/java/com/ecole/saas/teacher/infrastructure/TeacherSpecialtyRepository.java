package com.ecole.saas.teacher.infrastructure;

import com.ecole.saas.teacher.domain.TeacherSpecialty;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherSpecialtyRepository extends JpaRepository<TeacherSpecialty, UUID> {

    List<TeacherSpecialty> findByTeacherIdOrderByPrimarySpecialtyDescCreatedAtAsc(UUID teacherId);

    Optional<TeacherSpecialty> findByTeacherIdAndSubjectId(UUID teacherId, UUID subjectId);

    boolean existsByTeacherIdAndSubjectId(UUID teacherId, UUID subjectId);

    Optional<TeacherSpecialty> findByTeacherIdAndPrimarySpecialtyTrue(UUID teacherId);
}
