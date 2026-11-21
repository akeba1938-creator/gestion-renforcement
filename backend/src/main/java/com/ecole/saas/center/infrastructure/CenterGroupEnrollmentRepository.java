package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterGroupEnrollmentRepository extends JpaRepository<CenterGroupEnrollment, UUID> {

    interface GroupEnrollmentCount {
        UUID getGroupId();

        long getEnrollmentCount();
    }

    List<CenterGroupEnrollment> findByGroupIdOrderByEnrolledAtAscCreatedAtAsc(UUID groupId);

    List<CenterGroupEnrollment> findByGroupIdAndStatus(UUID groupId, CenterGroupEnrollmentStatus status);

    List<CenterGroupEnrollment> findByStudentIdAndStatus(UUID studentId, CenterGroupEnrollmentStatus status);

    List<CenterGroupEnrollment> findByStudentIdOrderByEnrolledAtDesc(UUID studentId);

    Optional<CenterGroupEnrollment> findByGroupIdAndStudentIdAndStatus(
            UUID groupId, UUID studentId, CenterGroupEnrollmentStatus status);

    Optional<CenterGroupEnrollment> findByIdAndGroupId(UUID id, UUID groupId);

    boolean existsByGroupIdAndStudentIdAndStatus(
            UUID groupId, UUID studentId, CenterGroupEnrollmentStatus status);

    long countByGroupIdAndStatus(UUID groupId, CenterGroupEnrollmentStatus status);

    @Query("""
            SELECT e.groupId AS groupId, COUNT(e.id) AS enrollmentCount
            FROM CenterGroupEnrollment e
            WHERE e.groupId IN :groupIds AND e.status = :status
            GROUP BY e.groupId
            """)
    List<GroupEnrollmentCount> countByGroupIdInAndStatus(
            @Param("groupIds") Collection<UUID> groupIds,
            @Param("status") CenterGroupEnrollmentStatus status);

    List<CenterGroupEnrollment> findByGroupIdInAndStatus(
            Collection<UUID> groupIds, CenterGroupEnrollmentStatus status);
}
