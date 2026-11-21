package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CenterStudentPackRepository
        extends JpaRepository<CenterStudentPack, UUID>, JpaSpecificationExecutor<CenterStudentPack> {

    interface ActivePackCandidate {
        UUID getId();

        UUID getStudentId();

        UUID getGroupId();

        LocalDate getStartDate();

        LocalDate getEndDate();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CenterStudentPack p WHERE p.id = :id")
    Optional<CenterStudentPack> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CenterStudentPack p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<CenterStudentPack> findByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT p.id AS id,
                   p.studentId AS studentId,
                   p.groupId AS groupId,
                   p.startDate AS startDate,
                   p.endDate AS endDate
            FROM CenterStudentPack p
            WHERE p.studentId IN :studentIds AND p.status = :status
            ORDER BY p.startDate ASC
            """)
    List<ActivePackCandidate> findActiveCandidatesByStudentIdInAndStatus(
            @Param("studentIds") Collection<UUID> studentIds,
            @Param("status") CenterStudentPackStatus status);

    List<CenterStudentPack> findByStudentIdAndStatusOrderByStartDateAsc(
            UUID studentId, CenterStudentPackStatus status);

    List<CenterStudentPack> findByStudentIdOrderByStartDateDesc(UUID studentId);
}
