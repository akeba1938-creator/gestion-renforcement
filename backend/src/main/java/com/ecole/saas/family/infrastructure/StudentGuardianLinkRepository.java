package com.ecole.saas.family.infrastructure;

import com.ecole.saas.family.domain.StudentGuardianLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentGuardianLinkRepository extends JpaRepository<StudentGuardianLink, UUID> {

    boolean existsByStudentIdAndGuardianId(UUID studentId, UUID guardianId);

    Optional<StudentGuardianLink> findByIdAndStudentId(UUID id, UUID studentId);

    Page<StudentGuardianLink> findByStudentId(UUID studentId, Pageable pageable);

    Page<StudentGuardianLink> findByStudentIdAndActiveTrue(UUID studentId, Pageable pageable);

    List<StudentGuardianLink> findByStudentIdAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(UUID studentId);

    Optional<StudentGuardianLink> findByStudentIdAndActiveTrueAndPrimaryContactTrue(UUID studentId);

    Optional<StudentGuardianLink> findByStudentIdAndActiveTrueAndFinancialResponsibleTrue(UUID studentId);

    Page<StudentGuardianLink> findByGuardianId(UUID guardianId, Pageable pageable);

    Page<StudentGuardianLink> findByGuardianIdAndActiveTrue(UUID guardianId, Pageable pageable);

    List<StudentGuardianLink> findByGuardianIdAndActiveTrue(UUID guardianId);

    List<StudentGuardianLink> findByGuardianIdInAndActiveTrue(Collection<UUID> guardianIds);
}
