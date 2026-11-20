package com.ecole.saas.student.infrastructure;

import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.StudentEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StudentEnrollmentRepository
        extends JpaRepository<StudentEnrollment, UUID>, JpaSpecificationExecutor<StudentEnrollment> {

    boolean existsByStudentIdAndAcademicYearIdAndStatus(
            UUID studentId, UUID academicYearId, EnrollmentStatus status);

    long countByClassRoomIdAndStatus(UUID classRoomId, EnrollmentStatus status);

    Optional<StudentEnrollment> findByStudentIdAndAcademicYearIdAndStatus(
            UUID studentId, UUID academicYearId, EnrollmentStatus status);

    List<StudentEnrollment> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(
            UUID studentId, UUID academicYearId);

    List<StudentEnrollment> findByClassRoomIdAndStatusOrderByCreatedAtAsc(
            UUID classRoomId, EnrollmentStatus status);

    Page<StudentEnrollment> findByStudentId(UUID studentId, Pageable pageable);
}
