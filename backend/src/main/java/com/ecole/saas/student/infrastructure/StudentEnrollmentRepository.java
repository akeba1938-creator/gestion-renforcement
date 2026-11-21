package com.ecole.saas.student.infrastructure;

import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.StudentEnrollment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentEnrollmentRepository
        extends JpaRepository<StudentEnrollment, UUID>, JpaSpecificationExecutor<StudentEnrollment> {

    boolean existsByStudentIdAndAcademicYearIdAndStatus(
            UUID studentId, UUID academicYearId, EnrollmentStatus status);

    boolean existsByStudentIdAndClassRoomIdInAndStatus(
            UUID studentId, Collection<UUID> classRoomIds, EnrollmentStatus status);

    long countByClassRoomIdAndStatus(UUID classRoomId, EnrollmentStatus status);

    @Query("""
            SELECT e.classRoomId, COUNT(e)
            FROM StudentEnrollment e
            WHERE e.classRoomId IN :classRoomIds AND e.status = :status
            GROUP BY e.classRoomId
            """)
    List<Object[]> countByClassRoomIdsAndStatus(
            @Param("classRoomIds") Collection<UUID> classRoomIds,
            @Param("status") EnrollmentStatus status);

    Optional<StudentEnrollment> findByStudentIdAndAcademicYearIdAndStatus(
            UUID studentId, UUID academicYearId, EnrollmentStatus status);

    List<StudentEnrollment> findByAcademicYearIdAndStudentIdInAndStatus(
            UUID academicYearId, Collection<UUID> studentIds, EnrollmentStatus status);

    List<StudentEnrollment> findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(
            UUID studentId, UUID academicYearId);

    List<StudentEnrollment> findByClassRoomIdAndStatusOrderByCreatedAtAsc(
            UUID classRoomId, EnrollmentStatus status);

    List<StudentEnrollment> findByClassRoomIdInAndStatus(
            Collection<UUID> classRoomIds, EnrollmentStatus status);

    Page<StudentEnrollment> findByStudentId(UUID studentId, Pageable pageable);

    Page<StudentEnrollment> findByStudentIdAndClassRoomIdIn(
            UUID studentId, Collection<UUID> classRoomIds, Pageable pageable);
}
