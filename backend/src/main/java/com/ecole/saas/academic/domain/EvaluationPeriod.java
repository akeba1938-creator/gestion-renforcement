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
@Table(name = "evaluation_periods")
public class EvaluationPeriod extends TenantScopedEntity {

    public static final int DEFAULT_WEIGHT = 1;
    public static final int MAX_WEIGHT = 100;

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,19}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodSystem periodType;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvaluationPeriodStatus status;

    @Column(name = "locked_at")
    private Instant lockedAt;

    protected EvaluationPeriod() {
    }

    public EvaluationPeriod(
            UUID academicYearId,
            String code,
            String name,
            PeriodSystem periodType,
            int sequenceOrder,
            LocalDate startDate,
            LocalDate endDate,
            int weight) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.code = normalizeCode(code);
        this.name = requireName(name);
        this.periodType = Objects.requireNonNull(periodType, "Le type de période est obligatoire.");
        this.sequenceOrder = validateSequenceOrder(sequenceOrder);
        this.startDate = Objects.requireNonNull(startDate, "La date de début de période est obligatoire.");
        this.endDate = Objects.requireNonNull(endDate, "La date de fin de période est obligatoire.");
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "La date de fin de la période doit être postérieure à la date de début.");
        }
        this.weight = validateWeight(weight);
        this.status = EvaluationPeriodStatus.PLANNED;
    }

    public void updateDetails(
            String newName,
            int newSequenceOrder,
            LocalDate newStartDate,
            LocalDate newEndDate,
            int newWeight) {
        if (this.status == EvaluationPeriodStatus.LOCKED) {
            throw new InvalidEvaluationPeriodTransitionException(
                    "Une période d'évaluation verrouillée ne peut plus être modifiée.");
        }
        this.name = requireName(newName);
        this.sequenceOrder = validateSequenceOrder(newSequenceOrder);
        LocalDate validatedStart = Objects.requireNonNull(newStartDate, "La date de début de période est obligatoire.");
        LocalDate validatedEnd = Objects.requireNonNull(newEndDate, "La date de fin de période est obligatoire.");
        if (!validatedEnd.isAfter(validatedStart)) {
            throw new IllegalArgumentException(
                    "La date de fin de la période doit être postérieure à la date de début.");
        }
        this.startDate = validatedStart;
        this.endDate = validatedEnd;
        this.weight = validateWeight(newWeight);
    }

    public void open() {
        if (this.status != EvaluationPeriodStatus.PLANNED) {
            throw new InvalidEvaluationPeriodTransitionException(
                    "Seule une période planifiée peut être ouverte (statut actuel : " + this.status + ").");
        }
        this.status = EvaluationPeriodStatus.OPEN;
    }

    public void close() {
        if (this.status != EvaluationPeriodStatus.OPEN) {
            throw new InvalidEvaluationPeriodTransitionException(
                    "Seule une période ouverte peut être clôturée (statut actuel : " + this.status + ").");
        }
        this.status = EvaluationPeriodStatus.CLOSED;
    }

    public void reopen() {
        if (this.status != EvaluationPeriodStatus.CLOSED) {
            throw new InvalidEvaluationPeriodTransitionException(
                    "Seule une période clôturée non verrouillée peut être rouverte (statut actuel : "
                            + this.status + ").");
        }
        this.status = EvaluationPeriodStatus.OPEN;
    }

    public void lock(Instant at) {
        if (this.status != EvaluationPeriodStatus.CLOSED) {
            throw new InvalidEvaluationPeriodTransitionException(
                    "Seule une période clôturée peut être verrouillée (statut actuel : " + this.status + ").");
        }
        this.lockedAt = Objects.requireNonNull(at, "La date de verrouillage est obligatoire.");
        this.status = EvaluationPeriodStatus.LOCKED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PeriodSystem getPeriodType() {
        return periodType;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getWeight() {
        return weight;
    }

    public EvaluationPeriodStatus getStatus() {
        return status;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Le code de la période d'évaluation est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Le code de la période doit contenir entre 1 et 20 caractères alphanumériques, tirets ou underscores.");
        }
        return normalized;
    }

    private static String requireName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Le nom de la période d'évaluation est obligatoire.");
        }
        String trimmed = rawName.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Le nom de la période ne peut pas dépasser 100 caractères.");
        }
        return trimmed;
    }

    private static int validateSequenceOrder(int order) {
        if (order <= 0) {
            throw new IllegalArgumentException("L'ordre de la période doit être strictement positif.");
        }
        return order;
    }

    private static int validateWeight(int weight) {
        if (weight <= 0 || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("Le poids de la période doit être compris entre 1 et " + MAX_WEIGHT + ".");
        }
        return weight;
    }
}
