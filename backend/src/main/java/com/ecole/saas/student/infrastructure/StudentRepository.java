package com.ecole.saas.student.infrastructure;

import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByMatricule(String matricule);

    Optional<Student> findByUserId(UUID userId);

    long countByStatus(StudentStatus status);
}
