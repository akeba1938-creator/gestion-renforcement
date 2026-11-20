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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "academic_years")
public class AcademicYear extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,19}$");

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
    @Column(name = "period_system", nullable = false, length = 20)
    private PeriodSystem periodSystem;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademicYearStatus status;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected AcademicYear() {
    }

    public AcademicYear(
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            AcademicYearStatus status,
            boolean current) {
        this(code, label, startDate, endDate, PeriodSystem.TRIMESTER, status, current);
    }

    public AcademicYear(
            String code,
            String label,
            LocalDate startDate,
            LocalDate endDate,
            PeriodSystem periodSystem,
            AcademicYearStatus status,
            boolean current) {
        this.code = normalizeCode(code);
        this.label = requireLabel(label);
        this.startDate = Objects.requireNonNull(startDate, "La date de début est obligatoire.");
        this.endDate = Objects.requireNonNull(endDate, "La date de fin est obligatoire.");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        this.periodSystem = periodSystem != null ? periodSystem : PeriodSystem.TRIMESTER;
        this.status = Objects.requireNonNull(status, "Le statut de l'année scolaire est obligatoire.");
        if (current && this.status != AcademicYearStatus.ACTIVE) {
            throw new IllegalArgumentException("Seule une année scolaire active peut être définie comme année courante.");
        }
        this.current = current;
    }

    public void updateLabel(String newLabel) {
        assertMutable();
        this.label = requireLabel(newLabel);
    }

    public void updateDetails(String newLabel, LocalDate newStartDate, LocalDate newEndDate, PeriodSystem newPeriodSystem) {
        assertMutable();
        this.label = requireLabel(newLabel);
        LocalDate validatedStart = Objects.requireNonNull(newStartDate, "La date de début est obligatoire.");
        LocalDate validatedEnd = Objects.requireNonNull(newEndDate, "La date de fin est obligatoire.");
        if (!validatedEnd.isAfter(validatedStart)) {
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
        }
        this.startDate = validatedStart;
        this.endDate = validatedEnd;
        if (newPeriodSystem != null) {
            this.periodSystem = newPeriodSystem;
        }
    }

    public void activate(boolean makeCurrent) {
        if (this.status != AcademicYearStatus.DRAFT) {
            throw new InvalidAcademicYearTransitionException(
                    "Seule une année scolaire en brouillon peut être activée (statut actuel : " + this.status + ").");
        }
        this.status = AcademicYearStatus.ACTIVE;
        this.current = makeCurrent;
    }

    public void markAsCurrent() {
        if (this.status != AcademicYearStatus.ACTIVE) {
            throw new InvalidAcademicYearTransitionException(
                    "Seule une année scolaire active peut être définie comme année courante.");
        }
        this.current = true;
    }

    public void unmarkAsCurrent() {
        this.current = false;
    }

    public void close(Instant at) {
        if (this.status != AcademicYearStatus.ACTIVE) {
            throw new InvalidAcademicYearTransitionException(
                    "Seule une année scolaire active peut être clôturée (statut actuel : " + this.status + ").");
        }
        this.closedAt = Objects.requireNonNull(at, "La date de clôture est obligatoire.");
        this.status = AcademicYearStatus.CLOSED;
        this.current = false;
    }

    public void archive(Instant at) {
        if (this.status != AcademicYearStatus.DRAFT && this.status != AcademicYearStatus.CLOSED) {
            throw new InvalidAcademicYearTransitionException(
                    "Seule une année scolaire en brouillon ou clôturée peut être archivée (statut actuel : "
                            + this.status + ").");
        }
        this.archivedAt = Objects.requireNonNull(at, "La date d'archivage est obligatoire.");
        this.status = AcademicYearStatus.ARCHIVED;
        this.current = false;
    }

    public boolean isMutable() {
        return this.status == AcademicYearStatus.DRAFT || this.status == AcademicYearStatus.ACTIVE;
    }

    public void assertMutable() {
        if (!isMutable()) {
            throw new AcademicYearImmutableException(
                    "Une année scolaire clôturée ou archivée ne peut plus être modifiée.");
        }
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

    public PeriodSystem getPeriodSystem() {
        return periodSystem;
    }

    public AcademicYearStatus getStatus() {
        return status;
    }

    public boolean isCurrent() {
        return current;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de l'année scolaire est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de l'année scolaire doit contenir entre 2 et 20 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    private static String requireLabel(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            throw new IllegalArgumentException("Le libellé de l'année scolaire est obligatoire.");
        }
        String trimmed = rawLabel.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Le libellé de l'année scolaire ne peut pas dépasser 100 caractères.");
        }
        return trimmed;
    }
}
