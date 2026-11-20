package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.ClassRoom;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClassRoomRepository
        extends JpaRepository<ClassRoom, UUID>, JpaSpecificationExecutor<ClassRoom> {

    boolean existsByAcademicYearIdAndCode(UUID academicYearId, String code);
}
