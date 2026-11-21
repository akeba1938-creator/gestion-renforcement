package com.ecole.saas.teacher.infrastructure;

import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TeacherRepository extends JpaRepository<Teacher, UUID>, JpaSpecificationExecutor<Teacher> {

    Optional<Teacher> findByEmployeeCode(String employeeCode);

    List<Teacher> findByEmployeeCodeIn(Collection<String> employeeCodes);

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Teacher> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndIdNot(UUID userId, UUID id);

    List<Teacher> findByStatusOrderByLastNameAscFirstNameAsc(TeacherStatus status);
}
