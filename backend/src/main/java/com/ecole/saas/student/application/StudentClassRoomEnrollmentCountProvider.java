package com.ecole.saas.student.application;

import com.ecole.saas.academic.ClassRoomEnrollmentCountProvider;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StudentClassRoomEnrollmentCountProvider implements ClassRoomEnrollmentCountProvider {

    private final StudentEnrollmentRepository enrollmentRepository;

    public StudentClassRoomEnrollmentCountProvider(StudentEnrollmentRepository enrollmentRepository) {
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    public long countActiveEnrollmentsByClassRoom(UUID classRoomId) {
        return enrollmentRepository.countByClassRoomIdAndStatus(classRoomId, EnrollmentStatus.ACTIVE);
    }
}
