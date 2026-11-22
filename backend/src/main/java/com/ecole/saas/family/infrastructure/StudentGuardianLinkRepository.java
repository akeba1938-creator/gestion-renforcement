package com.ecole.saas.family.infrastructure;

import com.ecole.saas.family.domain.StudentGuardianLink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    Page<StudentGuardianLink> findByGuardianIdAndStudentIdIn(
            UUID guardianId, Collection<UUID> studentIds, Pageable pageable);

    Page<StudentGuardianLink> findByGuardianIdAndStudentIdInAndActiveTrue(
            UUID guardianId, Collection<UUID> studentIds, Pageable pageable);

    boolean existsByGuardianIdAndStudentIdInAndActiveTrue(UUID guardianId, Collection<UUID> studentIds);

    List<StudentGuardianLink> findByGuardianIdAndActiveTrue(UUID guardianId);

    List<StudentGuardianLink> findByGuardianIdInAndActiveTrue(Collection<UUID> guardianIds);

    List<StudentGuardianLink> findByStudentIdInAndActiveTrueOrderByPrimaryContactDescCreatedAtAsc(
            Collection<UUID> studentIds);

    @Query(
            value = """
                    SELECT DISTINCT l.studentId
                    FROM StudentGuardianLink l
                    JOIN Guardian g ON g.id = l.guardianId
                    WHERE g.familyId = :familyId
                      AND l.active = true
                    ORDER BY l.studentId ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT l.studentId)
                    FROM StudentGuardianLink l
                    JOIN Guardian g ON g.id = l.guardianId
                    WHERE g.familyId = :familyId
                      AND l.active = true
                    """)
    Page<UUID> findDistinctActiveStudentIdsByFamilyId(
            @Param("familyId") UUID familyId, Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT l.studentId
                    FROM StudentGuardianLink l
                    JOIN Guardian g ON g.id = l.guardianId
                    WHERE g.familyId = :familyId
                      AND l.active = true
                      AND l.studentId IN :visibleStudentIds
                    ORDER BY l.studentId ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT l.studentId)
                    FROM StudentGuardianLink l
                    JOIN Guardian g ON g.id = l.guardianId
                    WHERE g.familyId = :familyId
                      AND l.active = true
                      AND l.studentId IN :visibleStudentIds
                    """)
    Page<UUID> findDistinctActiveStudentIdsByFamilyIdAndStudentIdIn(
            @Param("familyId") UUID familyId,
            @Param("visibleStudentIds") Collection<UUID> visibleStudentIds,
            Pageable pageable);
}
