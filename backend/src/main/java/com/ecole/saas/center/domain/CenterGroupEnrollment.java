package com.ecole.saas.center.domain;

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

/**
 * Inscription d'un élève dans une cohorte de renforcement (avec lien optionnel vers son pack).
 */
@Entity
@Table(name = "center_group_enrollments")
public class CenterGroupEnrollment extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "student_pack_id")
    private UUID studentPackId;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDate enrolledAt;

    @Column(name = "ended_at")
    private LocalDate endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CenterGroupEnrollmentStatus status = CenterGroupEnrollmentStatus.ACTIVE;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "notes", length = 500)
    private String notes;

    protected CenterGroupEnrollment() {
    }

    public CenterGroupEnrollment(
            UUID groupId,
            UUID studentId,
            UUID studentPackId,
            LocalDate enrolledAt,
            String notes) {
        this.groupId = Objects.requireNonNull(groupId, "La cohorte est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.studentPackId = studentPackId;
        if (enrolledAt == null) {
            throw new CenterValidationException(
                    "center-enrollment-invalid",
                    "Date d'inscription obligatoire",
                    "La date d'inscription dans la cohorte est obligatoire.");
        }
        this.enrolledAt = enrolledAt;
        this.status = CenterGroupEnrollmentStatus.ACTIVE;
        this.notes = normalizeOptional(notes, 500);
    }

    public void attachStudentPack(UUID studentPackId) {
        if (this.status != CenterGroupEnrollmentStatus.ACTIVE) {
            throw new CenterConflictException(
                    "center-enrollment-inactive",
                    "Inscription inactive",
                    "Impossible d'associer un pack à une inscription qui n'est plus ACTIVE.");
        }
        this.studentPackId = studentPackId;
    }

    public void transitionStatus(CenterGroupEnrollmentStatus targetStatus, LocalDate endDate, String reason) {
        if (targetStatus == null || targetStatus == CenterGroupEnrollmentStatus.ACTIVE) {
            throw new CenterValidationException(
                    "center-enrollment-invalid",
                    "Statut cible invalide",
                    "Le statut cible d'une sortie de cohorte doit être COMPLETED, WITHDRAWN ou CANCELLED.");
        }
        if (this.status != CenterGroupEnrollmentStatus.ACTIVE) {
            throw new CenterConflictException(
                    "center-enrollment-invalid-transition",
                    "Transition d'inscription invalide",
                    "Seule une inscription ACTIVE peut changer de statut (statut actuel : " + this.status + ").");
        }
        LocalDate resolvedEndDate = endDate != null ? endDate : this.enrolledAt;
        if (resolvedEndDate.isBefore(this.enrolledAt)) {
            throw new CenterValidationException(
                    "center-enrollment-invalid",
                    "Date de fin invalide",
                    "La date de fin d'inscription ne peut pas être antérieure à la date d'inscription.");
        }
        if ((targetStatus == CenterGroupEnrollmentStatus.WITHDRAWN
                        || targetStatus == CenterGroupEnrollmentStatus.CANCELLED)
                && (reason == null || reason.isBlank())) {
            throw new CenterValidationException(
                    "center-enrollment-invalid",
                    "Motif obligatoire",
                    "Un motif est obligatoire pour un retrait ou une annulation d'inscription.");
        }
        this.status = targetStatus;
        this.endedAt = resolvedEndDate;
        this.statusReason = normalizeOptional(reason, 255);
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-enrollment-invalid",
                    "Inscription invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getStudentPackId() {
        return studentPackId;
    }

    public LocalDate getEnrolledAt() {
        return enrolledAt;
    }

    public LocalDate getEndedAt() {
        return endedAt;
    }

    public CenterGroupEnrollmentStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getNotes() {
        return notes;
    }
}
