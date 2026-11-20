package com.ecole.saas.student.application;

import com.ecole.saas.academic.AcademicStructurePort;
import com.ecole.saas.academic.AcademicYearSummary;
import com.ecole.saas.shared.domain.TenantContext;
import com.ecole.saas.student.StudentDirectoryPort;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.student.domain.EnrollmentStatus;
import com.ecole.saas.student.domain.MatriculeFormatter;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.domain.StudentStatus;
import com.ecole.saas.student.infrastructure.StudentEnrollmentRepository;
import com.ecole.saas.student.infrastructure.StudentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation de la frontière publique {@link StudentDirectoryPort} consommée par les autres modules.
 */
@Service
public class StudentDirectoryQueryService implements StudentDirectoryPort {

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository enrollmentRepository;
    private final AcademicStructurePort academicStructurePort;

    public StudentDirectoryQueryService(
            StudentRepository studentRepository,
            StudentEnrollmentRepository enrollmentRepository,
            AcademicStructurePort academicStructurePort) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.academicStructurePort = academicStructurePort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> findStudentById(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        return studentRepository.findById(studentId).map(StudentDirectoryQueryService::toStudentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentSummary> findStudentByMatricule(String matricule) {
        TenantContext.requireCurrent();
        if (matricule == null || matricule.isBlank()) {
            return Optional.empty();
        }
        String normalized;
        try {
            normalized = MatriculeFormatter.normalize(matricule);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return studentRepository.findByMatricule(normalized).map(StudentDirectoryQueryService::toStudentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSummary> findStudentsByIds(Collection<UUID> studentIds) {
        TenantContext.requireCurrent();
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        return studentRepository.findAllById(studentIds).stream()
                .map(StudentDirectoryQueryService::toStudentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentEnrollmentSummary> findEnrollmentByStudentAndYear(UUID studentId, UUID academicYearId) {
        TenantContext.requireCurrent();
        if (studentId == null || academicYearId == null) {
            return Optional.empty();
        }
        return enrollmentRepository
                .findByStudentIdAndAcademicYearIdAndStatus(studentId, academicYearId, EnrollmentStatus.ACTIVE)
                .or(() -> enrollmentRepository
                        .findByStudentIdAndAcademicYearIdOrderByCreatedAtDesc(studentId, academicYearId)
                        .stream()
                        .findFirst())
                .map(StudentDirectoryQueryService::toEnrollmentSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudentEnrollmentSummary> findCurrentEnrollmentForStudent(UUID studentId) {
        TenantContext.requireCurrent();
        if (studentId == null) {
            return Optional.empty();
        }
        Optional<AcademicYearSummary> currentYear = academicStructurePort.findCurrentAcademicYear();
        if (currentYear.isEmpty()) {
            return Optional.empty();
        }
        return findEnrollmentByStudentAndYear(studentId, currentYear.get().id());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentEnrollmentSummary> findActiveEnrollmentsByClassRoom(UUID classRoomId) {
        TenantContext.requireCurrent();
        if (classRoomId == null) {
            return List.of();
        }
        return enrollmentRepository.findByClassRoomIdAndStatusOrderByCreatedAtAsc(classRoomId, EnrollmentStatus.ACTIVE)
                .stream()
                .map(StudentDirectoryQueryService::toEnrollmentSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveStudents() {
        TenantContext.requireCurrent();
        return studentRepository.countByStatus(StudentStatus.ACTIVE);
    }

    private static StudentSummary toStudentSummary(Student student) {
        return new StudentSummary(
                student.getId(),
                student.getMatricule(),
                student.getFirstName(),
                student.getLastName(),
                student.getGender().name(),
                student.getBirthDate(),
                student.getStatus().name(),
                student.isActive());
    }

    private static StudentEnrollmentSummary toEnrollmentSummary(StudentEnrollment enrollment) {
        return new StudentEnrollmentSummary(
                enrollment.getId(),
                enrollment.getStudentId(),
                enrollment.getAcademicYearId(),
                enrollment.getClassRoomId(),
                enrollment.getEnrollmentType().name(),
                enrollment.getStatus().name(),
                enrollment.getEnrollmentDate());
    }
}
