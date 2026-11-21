package com.ecole.saas.teacher.application;

import com.ecole.saas.teacher.ImportTeacherCommand;
import com.ecole.saas.teacher.TeacherImportPort;
import com.ecole.saas.teacher.TeacherSummary;
import com.ecole.saas.teacher.domain.Teacher;
import com.ecole.saas.teacher.domain.TeacherGender;
import com.ecole.saas.teacher.domain.TeacherType;
import com.ecole.saas.teacher.infrastructure.TeacherSpecialtyRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Adaptateur applicatif de {@link TeacherImportPort} déléguant à {@link TeacherService}
 * afin de préserver intégralement RBAC, validations domaine et journalisation d'audit.
 */
@Service
public class TeacherImportPortAdapter implements TeacherImportPort {

    private final TeacherService teacherService;
    private final TeacherSpecialtyRepository specialtyRepository;

    public TeacherImportPortAdapter(
            TeacherService teacherService,
            TeacherSpecialtyRepository specialtyRepository) {
        this.teacherService = teacherService;
        this.specialtyRepository = specialtyRepository;
    }

    @Override
    public TeacherSummary createTeacherForImport(ImportTeacherCommand command) {
        Teacher saved = teacherService.createTeacher(
                command.employeeCode(),
                null,
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                parseTeacherType(command.teacherType()),
                command.phone(),
                command.secondaryPhone(),
                command.email(),
                command.address(),
                command.nationalIdNumber(),
                command.hireDate(),
                command.hourlyRateXof(),
                command.notes());
        if (command.primarySubjectId() != null) {
            teacherService.addSpecialty(saved.getId(), command.primarySubjectId(), true, null);
        }
        return toSummary(saved);
    }

    @Override
    public TeacherSummary updateTeacherForImport(UUID teacherId, ImportTeacherCommand command) {
        Teacher saved = teacherService.updateTeacher(
                teacherId,
                command.firstName(),
                command.lastName(),
                parseGender(command.gender()),
                parseTeacherType(command.teacherType()),
                command.phone(),
                command.secondaryPhone(),
                command.email(),
                command.address(),
                command.nationalIdNumber(),
                command.hireDate(),
                command.hourlyRateXof(),
                command.notes());
        if (command.primarySubjectId() != null
                && !specialtyRepository.existsByTeacherIdAndSubjectId(saved.getId(), command.primarySubjectId())) {
            teacherService.addSpecialty(saved.getId(), command.primarySubjectId(), true, null);
        }
        return toSummary(saved);
    }

    private static TeacherGender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return TeacherGender.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static TeacherType parseTeacherType(String raw) {
        if (raw == null || raw.isBlank()) {
            return TeacherType.PERMANENT;
        }
        return TeacherType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static TeacherSummary toSummary(Teacher teacher) {
        return new TeacherSummary(
                teacher.getId(),
                teacher.getEmployeeCode(),
                teacher.getUserId(),
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getTeacherType().name(),
                teacher.getPhone(),
                teacher.getEmail(),
                teacher.getHourlyRateXof(),
                teacher.getStatus().name(),
                teacher.isActive());
    }
}
