package com.ecole.saas.student.infrastructure;

import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByMatricule(String matricule);

    List<Student> findByMatriculeIn(Collection<String> matricules);

    @Query("SELECT s FROM Student s WHERE LOWER(s.lastName) IN :lowerLastNames")
    List<Student> findByLastNameLowerIn(@Param("lowerLastNames") Collection<String> lowerLastNames);

    Optional<Student> findByUserId(UUID userId);

    long countByStatus(StudentStatus status);
}
