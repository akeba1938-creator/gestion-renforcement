package com.ecole.saas.academic.infrastructure;

import com.ecole.saas.academic.domain.ClassSubject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, UUID> {

    Page<ClassSubject> findByClassRoomId(UUID classRoomId, Pageable pageable);

    Page<ClassSubject> findByClassRoomIdAndActiveTrue(UUID classRoomId, Pageable pageable);

    List<ClassSubject> findByClassRoomIdAndActiveTrueOrderByCreatedAtAsc(UUID classRoomId);

    Optional<ClassSubject> findByIdAndClassRoomId(UUID id, UUID classRoomId);

    boolean existsByClassRoomIdAndSubjectId(UUID classRoomId, UUID subjectId);
}
