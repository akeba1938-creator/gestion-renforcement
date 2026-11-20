package com.ecole.saas.student.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "student_enrollments")
public class StudentEnrollment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "class_room_id", nullable = false)
    private UUID classRoomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_type", nullable = false, length = 20)
    private EnrollmentType enrollmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(name = "enrollment_date", nullable = false)
    private LocalDate enrollmentDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "previous_school", length = 150)
    private String previousSchool;

    @Column(name = "repeating", nullable = false)
    private boolean repeating;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    protected StudentEnrollment() {
    }

    public StudentEnrollment(
            UUID studentId,
            UUID academicYearId,
            UUID classRoomId,
            EnrollmentType enrollmentType,
            LocalDate enrollmentDate,
            String previousSchool,
            boolean repeating,
            String notes) {
        this.studentId = Objects.requireNonNull(studentId, "L'identifiant de l'élève est obligatoire.");
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.classRoomId = Objects.requireNonNull(classRoomId, "La classe est obligatoire.");
        this.enrollmentType = Objects.requireNonNull(enrollmentType, "Le type d'inscription est obligatoire.");
        this.enrollmentDate = Objects.requireNonNull(enrollmentDate, "La date d'inscription est obligatoire.");
        this.previousSchool = normalizeOptional(previousSchool, 150, "L'établissement d'origine");
        this.repeating = repeating;
        this.notes = normalizeOptional(notes, 500, "Les observations");
        this.status = EnrollmentStatus.ACTIVE;
    }

    public void transferClass(UUID targetClassRoomId, String reason) {
        assertActive();
        UUID target = Objects.requireNonNull(targetClassRoomId, "La classe cible est obligatoire.");
        if (target.equals(this.classRoomId)) {
            throw new IllegalArgumentException("L'élève est déjà affecté à cette classe.");
        }
        this.classRoomId = target;
        this.statusReason = normalizeOptional(reason, 255, "Le motif de changement de classe");
    }

    public void complete(LocalDate completionDate, String reason) {
        assertActive();
        this.endDate = validateEndDate(completionDate);
        this.statusReason = normalizeOptional(reason, 255, "Le motif");
        this.status = EnrollmentStatus.COMPLETED;
    }

    public void withdraw(LocalDate withdrawalDate, String reason) {
        assertActive();
        this.statusReason = requireReason(reason, "Le motif de retrait est obligatoire.");
        this.endDate = validateEndDate(withdrawalDate);
        this.status = EnrollmentStatus.WITHDRAWN;
    }

    public void transferOut(LocalDate transferDate, String reason) {
        assertActive();
        this.statusReason = requireReason(reason, "Le motif de transfert est obligatoire.");
        this.endDate = validateEndDate(transferDate);
        this.status = EnrollmentStatus.TRANSFERRED;
    }

    public void cancel(String reason) {
        assertActive();
        this.statusReason = requireReason(reason, "Le motif d'annulation est obligatoire.");
        this.status = EnrollmentStatus.CANCELLED;
    }

    public boolean isActive() {
        return this.status == EnrollmentStatus.ACTIVE;
    }

    public void assertActive() {
        if (this.status != EnrollmentStatus.ACTIVE) {
            throw new InvalidEnrollmentStateException(
                    "Seule une inscription active peut être modifiée (statut actuel : " + this.status + ").");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getClassRoomId() {
        return classRoomId;
    }

    public EnrollmentType getEnrollmentType() {
        return enrollmentType;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getPreviousSchool() {
        return previousSchool;
    }

    public boolean isRepeating() {
        return repeating;
    }

    public String getNotes() {
        return notes;
    }

    public String getStatusReason() {
        return statusReason;
    }

    private LocalDate validateEndDate(LocalDate candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate.isBefore(this.enrollmentDate)) {
            throw new IllegalArgumentException(
                    "La date de fin d'inscription ne peut pas être antérieure à la date d'inscription.");
        }
        return candidate;
    }

    private static String requireReason(String raw, String blankMessage) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(blankMessage);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 255) {
            throw new IllegalArgumentException("Le motif ne peut pas dépasser 255 caractères.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLength, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldLabel + " ne peut pas dépasser " + maxLength + " caractères.");
        }
        return trimmed;
    }
}
