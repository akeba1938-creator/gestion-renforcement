package com.ecole.saas.center.infrastructure;

import com.ecole.saas.center.domain.CenterStudentPack;
import com.ecole.saas.center.domain.CenterStudentPackStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CenterStudentPackRepository
        extends JpaRepository<CenterStudentPack, UUID>, JpaSpecificationExecutor<CenterStudentPack> {

    List<CenterStudentPack> findByStudentIdAndStatusOrderByStartDateAsc(
            UUID studentId, CenterStudentPackStatus status);

    List<CenterStudentPack> findByStudentIdOrderByStartDateDesc(UUID studentId);
}
