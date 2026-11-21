package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import jakarta.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM CenterStudentPack p WHERE p.id = :id")
    Optional<CenterStudentPack> findByIdForUpdate(@Param("id") UUID id);

    List<CenterStudentPack> findByStudentIdAndStatusOrderByStartDateAsc(
            UUID studentId, CenterStudentPackStatus status);

    List<CenterStudentPack> findByStudentIdOrderByStartDateDesc(UUID studentId);
}
