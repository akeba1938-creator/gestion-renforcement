package com.ecole.saas.student.application;

import com.ecole.saas.student.ImportEnrollmentCommand;
import com.ecole.saas.student.ImportStudentCommand;
import com.ecole.saas.student.StudentEnrollmentSummary;
import com.ecole.saas.student.StudentImportPort;
import com.ecole.saas.student.StudentSummary;
import com.ecole.saas.student.domain.EnrollmentType;
import com.ecole.saas.student.domain.Student;
import com.ecole.saas.student.domain.StudentEnrollment;
import com.ecole.saas.student.domain.StudentGender;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Adaptateur applicatif de {@link StudentImportPort} déléguant aux services métier du module
 * afin de préserver intégralement les contrôles RBAC, quotas élèves, verrous et audit.
 */
@Service
public class StudentImportPortAdapter implements StudentImportPort {

    private final StudentService studentService;
    private final StudentEnrollmentService enrollmentService;

    public StudentImportPortAdapter(
            StudentService studentService,
            StudentEnrollmentService enrollmentService) {
        this.studentService = studentService;
        this.enrollmentService = enrollmentService;
    }

    @Override
    public StudentSummary createStudentForImport(ImportStudentCommand command) {
        Student saved = studentService.createStudent(
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                command.birthDate(),
                command.birthPlace(),
                command.nationality(),
                command.address(),
                command.phone(),
                command.email(),
                command.medicalNotes(),
                command.admissionDate());
        return toStudentSummary(saved);
    }

    @Override
    public StudentSummary updateStudentForImport(UUID studentId, ImportStudentCommand command) {
        Student saved = studentService.updateStudent(
                studentId,
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                command.birthDate(),
                command.birthPlace(),
                command.nationality(),
                command.address(),
                command.phone(),
                command.email(),
                command.medicalNotes(),
                command.admissionDate());
        return toStudentSummary(saved);
    }

    @Override
    public StudentEnrollmentSummary enrollStudentForImport(ImportEnrollmentCommand command) {
        StudentEnrollmentService.EnrollmentView view = enrollmentService.enrollStudent(
                command.studentId(),
                command.academicYearId(),
                command.classRoomId(),
                parseEnrollmentType(command.enrollmentType()),
                command.enrollmentDate(),
                command.previousSchool(),
                command.repeating(),
                command.notes());
        return toEnrollmentSummary(view.enrollment());
    }

    @Override
    public StudentEnrollmentSummary transferClassForImport(UUID enrollmentId, UUID targetClassRoomId, String reason) {
        StudentEnrollmentService.EnrollmentView view = enrollmentService.transferClass(
                enrollmentId,
                targetClassRoomId,
                reason);
        return toEnrollmentSummary(view.enrollment());
    }

    private static StudentGender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Le genre de l'élève est obligatoire.");
        }
        return StudentGender.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static EnrollmentType parseEnrollmentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnrollmentType.NEW_ADMISSION;
        }
        return EnrollmentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
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
