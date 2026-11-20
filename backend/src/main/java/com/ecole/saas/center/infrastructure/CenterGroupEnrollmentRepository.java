package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterGroupEnrollment;
import com.ecole.saas.center.domain.CenterGroupEnrollmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CenterGroupEnrollmentRepository extends JpaRepository<CenterGroupEnrollment, UUID> {

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

    List<CenterGroupEnrollment> findByGroupIdInAndStatus(
            Collection<UUID> groupIds, CenterGroupEnrollmentStatus status);
}
