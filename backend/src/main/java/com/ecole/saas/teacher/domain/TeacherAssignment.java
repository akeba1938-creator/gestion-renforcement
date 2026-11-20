package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "teacher_assignments")
public class TeacherAssignment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "class_room_id", nullable = false, updatable = false)
    private UUID classRoomId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "class_subject_id", nullable = false, updatable = false)
    private UUID classSubjectId;

    @Column(name = "homeroom_teacher", nullable = false)
    private boolean homeroomTeacher;

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "notes", length = 500)
    private String notes;

    protected TeacherAssignment() {
    }

    public TeacherAssignment(
            UUID teacherId,
            UUID academicYearId,
            UUID classRoomId,
            UUID subjectId,
            UUID classSubjectId,
            boolean homeroomTeacher,
            Integer weeklyHours,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        this.teacherId = Objects.requireNonNull(teacherId, "L'enseignant est obligatoire.");
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.subjectId = Objects.requireNonNull(subjectId, "La matière est obligatoire.");
        this.classSubjectId = Objects.requireNonNull(classSubjectId, "La matière de classe est obligatoire.");
        this.homeroomTeacher = homeroomTeacher;
        this.weeklyHours = validateWeeklyHours(weeklyHours);
        validateDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.notes = normalizeNotes(notes);
        this.active = true;
    }

    public void updateDetails(
            boolean homeroomTeacher,
            Integer weeklyHours,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        assertActive();
        this.homeroomTeacher = homeroomTeacher;
        this.weeklyHours = validateWeeklyHours(weeklyHours);
        validateDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.notes = normalizeNotes(notes);
    }

    public void clearHomeroom() {
        this.homeroomTeacher = false;
    }

    public void deactivate(LocalDate endDate, String reason) {
        assertActive();
        if (endDate != null) {
            validateDates(this.startDate, endDate);
            this.endDate = endDate;
        }
        if (reason != null && !reason.isBlank()) {
            this.notes = normalizeNotes(reason);
        }
        this.homeroomTeacher = false;
        this.active = false;
    }

    public void assertActive() {
        if (!this.active) {
            throw new TeacherConflictException(
                    "teacher-assignment-inactive",
                    "Affectation inactive",
                    "Cette affectation enseignant est déjà désactivée.");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getClassSubjectId() {
        return classSubjectId;
    }

    public boolean isHomeroomTeacher() {
        return homeroomTeacher;
    }

    public Integer getWeeklyHours() {
        return weeklyHours;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public String getNotes() {
        return notes;
    }

    private static Integer validateWeeklyHours(Integer weeklyHours) {
        if (weeklyHours == null) {
            return null;
        }
        if (weeklyHours <= 0 || weeklyHours > 40) {
            throw new TeacherValidationException(
                    "Le volume horaire hebdomadaire doit être compris entre 1 et 40 heures.");
        }
        return weeklyHours;
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new TeacherValidationException(
                    "La date de fin d'affectation doit être postérieure ou égale à la date de début.");
        }
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > 500) {
            throw new TeacherValidationException("Les notes d'affectation ne doivent pas dépasser 500 caractères.");
        }
        return trimmed;
    }
}
