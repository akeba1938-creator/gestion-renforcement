package com.ecole.saas.academic.domain;

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
@Table(name = "academic_years")
public class AcademicYear extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademicYearStatus status;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    protected AcademicYear() {
    }

    public AcademicYear(
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            AcademicYearStatus status,
            boolean current) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code de l'année scolaire est obligatoire.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Le libellé de l'année scolaire est obligatoire.");
        }
        this.startDate = Objects.requireNonNull(startDate, "La date de début est obligatoire.");
        this.endDate = Objects.requireNonNull(endDate, "La date de fin est obligatoire.");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        this.status = Objects.requireNonNull(status, "Le statut de l'année scolaire est obligatoire.");
        this.code = code.trim();
        this.label = label.trim();
        this.current = current;
    }

    public void updateLabel(String newLabel) {
        if (newLabel == null || newLabel.isBlank()) {
            throw new IllegalArgumentException("Le libellé de l'année scolaire est obligatoire.");
        }
        this.label = newLabel.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public AcademicYearStatus getStatus() {
        return status;
    }

    public boolean isCurrent() {
        return current;
    }
}
