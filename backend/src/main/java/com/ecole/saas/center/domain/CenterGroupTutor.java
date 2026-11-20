package com.ecole.saas.center.domain;

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

/**
 * Affectation d'un intervenant / enseignant à une cohorte de renforcement.
 */
@Entity
@Table(name = "center_group_tutors")
public class CenterGroupTutor extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "lead_tutor", nullable = false)
    private boolean leadTutor;

    @Column(name = "hourly_rate_xof")
    private Long hourlyRateXof;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", length = 500)
    private String notes;

    protected CenterGroupTutor() {
    }

    public CenterGroupTutor(
            UUID groupId,
            UUID teacherId,
            UUID subjectId,
            boolean leadTutor,
            Long hourlyRateXof,
            LocalDate startDate,
            LocalDate endDate,
            String notes) {
        this.groupId = Objects.requireNonNull(groupId, "La cohorte est obligatoire.");
        this.teacherId = Objects.requireNonNull(teacherId, "L'intervenant est obligatoire.");
        this.subjectId = subjectId;
        this.leadTutor = leadTutor;
        this.hourlyRateXof = validateHourlyRate(hourlyRateXof);
        validateDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = true;
        this.notes = normalizeOptional(notes, 500);
    }

    public void unsetLeadTutor() {
        this.leadTutor = false;
    }

    public void deactivate(LocalDate endDate, String reason) {
        if (!this.active) {
            throw new CenterConflictException(
                    "center-tutor-assignment-inactive",
                    "Affectation déjà désactivée",
                    "Cette affectation d'intervenant est déjà inactive.");
        }
        if (endDate != null) {
            validateDates(this.startDate, endDate);
            this.endDate = endDate;
        }
        if (reason != null && !reason.isBlank()) {
            this.notes = normalizeOptional(reason, 500);
        }
        this.leadTutor = false;
        this.active = false;
    }

    private static Long validateHourlyRate(Long rate) {
        if (rate != null && rate < 0L) {
            throw new CenterValidationException(
                    "center-tutor-assignment-invalid",
                    "Taux horaire invalide",
                    "Le taux horaire en FCFA doit être un entier positif ou nul.");
        }
        return rate;
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new CenterValidationException(
                    "center-tutor-assignment-invalid",
                    "Période invalide",
                    "La date de fin d'affectation ne peut pas être antérieure à la date de début.");
        }
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-tutor-assignment-invalid",
                    "Affectation invalide",
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

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public boolean isLeadTutor() {
        return leadTutor;
    }

    public Long getHourlyRateXof() {
        return hourlyRateXof;
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
}
