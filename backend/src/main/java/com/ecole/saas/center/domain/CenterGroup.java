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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Groupe / cohorte de renforcement ou soutien scolaire (prépa examens, soutien disciplinaire, stage vacances, suivi individuel).
 */
@Entity
@Table(name = "center_groups")
public class CenterGroup extends TenantScopedEntity {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,29}$");
    public static final int MAX_CAPACITY = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "academic_year_id", nullable = false, updatable = false)
    private UUID academicYearId;

    @Column(name = "grade_level_id")
    private UUID gradeLevelId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "default_pack_id")
    private UUID defaultPackId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 30)
    private CenterGroupType groupType;

    @Column(name = "room_name", length = 60)
    private String roomName;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CenterGroupStatus status = CenterGroupStatus.DRAFT;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected CenterGroup() {
    }

    public CenterGroup(
            UUID academicYearId,
            UUID gradeLevelId,
            UUID subjectId,
            UUID defaultPackId,
            String code,
            String name,
            String description,
            CenterGroupType groupType,
            String roomName,
            int capacity,
            LocalDate startDate,
            LocalDate endDate) {
        this.academicYearId = Objects.requireNonNull(academicYearId, "L'année scolaire est obligatoire.");
        this.gradeLevelId = gradeLevelId;
        this.subjectId = subjectId;
        this.defaultPackId = defaultPackId;
        this.code = normalizeCode(code);
        this.name = requireText(name, "Le nom du groupe de renforcement est obligatoire.", 120);
        this.description = normalizeOptional(description, 500);
        this.groupType = requireGroupType(groupType);
        this.roomName = normalizeOptional(roomName, 60);
        this.capacity = validateCapacity(capacity);
        validateDates(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = CenterGroupStatus.DRAFT;
    }

    public void updateDetails(
            UUID gradeLevelId,
            UUID subjectId,
            UUID defaultPackId,
            String name,
            String description,
            CenterGroupType groupType,
            String roomName,
            int capacity,
            LocalDate startDate,
            LocalDate endDate,
            long currentActiveEnrollments) {
        if (this.status == CenterGroupStatus.COMPLETED || this.status == CenterGroupStatus.ARCHIVED) {
            throw new CenterConflictException(
                    "center-group-immutable",
                    "Cohorte non modifiable",
                    "Une cohorte clôturée ou archivée ne peut plus être modifiée.");
        }
        int validatedCapacity = validateCapacity(capacity);
        if (validatedCapacity < currentActiveEnrollments) {
            throw new CenterConflictException(
                    "center-group-capacity-exceeded",
                    "Capacité insuffisante",
                    "La capacité demandée (" + validatedCapacity
                            + ") est inférieure à l'effectif actuellement inscrit (" + currentActiveEnrollments + ").");
        }
        validateDates(startDate, endDate);
        this.gradeLevelId = gradeLevelId;
        this.subjectId = subjectId;
        this.defaultPackId = defaultPackId;
        this.name = requireText(name, "Le nom du groupe de renforcement est obligatoire.", 120);
        this.description = normalizeOptional(description, 500);
        this.groupType = requireGroupType(groupType);
        this.roomName = normalizeOptional(roomName, 60);
        this.capacity = validatedCapacity;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void activate() {
        if (this.status != CenterGroupStatus.DRAFT) {
            throw new CenterConflictException(
                    "center-group-invalid-transition",
                    "Transition de statut invalide",
                    "Seule une cohorte au statut DRAFT peut être activée (statut actuel : " + this.status + ").");
        }
        this.status = CenterGroupStatus.ACTIVE;
        this.statusReason = null;
    }

    public void complete(String reason) {
        if (this.status != CenterGroupStatus.ACTIVE) {
            throw new CenterConflictException(
                    "center-group-invalid-transition",
                    "Transition de statut invalide",
                    "Seule une cohorte au statut ACTIVE peut être clôturée (statut actuel : " + this.status + ").");
        }
        this.status = CenterGroupStatus.COMPLETED;
        this.statusReason = normalizeOptional(reason, 255);
    }

    public void archive(String reason, Instant now) {
        if (this.status == CenterGroupStatus.ARCHIVED) {
            throw new CenterConflictException(
                    "center-group-invalid-transition",
                    "Cohorte déjà archivée",
                    "Cette cohorte est déjà archivée.");
        }
        String validatedReason = requireText(reason, "Un motif est obligatoire pour archiver une cohorte.", 255);
        this.status = CenterGroupStatus.ARCHIVED;
        this.statusReason = validatedReason;
        this.archivedAt = Objects.requireNonNull(now, "L'horodatage d'archivage est obligatoire.");
    }

    public boolean isOperationalForEnrollmentsAndPlanning() {
        return this.status == CenterGroupStatus.DRAFT || this.status == CenterGroupStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == CenterGroupStatus.ACTIVE;
    }

    public static String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new CenterValidationException(
                    "center-group-invalid", "Cohorte invalide", "Le code de la cohorte est obligatoire.");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new CenterValidationException(
                    "center-group-invalid",
                    "Code cohorte invalide",
                    "Le code de la cohorte doit contenir 2 à 30 caractères alphanumériques majuscules ('-' et '_' autorisés).");
        }
        return normalized;
    }

    private static CenterGroupType requireGroupType(CenterGroupType type) {
        if (type == null) {
            throw new CenterValidationException(
                    "center-group-invalid", "Type de cohorte obligatoire", "Le type de cohorte est obligatoire.");
        }
        return type;
    }

    private static int validateCapacity(int cap) {
        if (cap < 1 || cap > MAX_CAPACITY) {
            throw new CenterValidationException(
                    "center-group-invalid",
                    "Capacité invalide",
                    "La capacité de la cohorte doit être comprise entre 1 et " + MAX_CAPACITY + " élèves.");
        }
        return cap;
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null) {
            throw new CenterValidationException(
                    "center-group-invalid", "Date de début obligatoire", "La date de début de la cohorte est obligatoire.");
        }
        if (end != null && end.isBefore(start)) {
            throw new CenterValidationException(
                    "center-group-invalid",
                    "Période invalide",
                    "La date de fin de la cohorte ne peut pas être antérieure à sa date de début.");
        }
    }

    private static String requireText(String raw, String message, int maxLen) {
        if (raw == null || raw.isBlank()) {
            throw new CenterValidationException("center-group-invalid", "Cohorte invalide", message);
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-group-invalid",
                    "Cohorte invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-group-invalid",
                    "Cohorte invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public UUID getGradeLevelId() {
        return gradeLevelId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getDefaultPackId() {
        return defaultPackId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CenterGroupType getGroupType() {
        return groupType;
    }

    public String getRoomName() {
        return roomName;
    }

    public int getCapacity() {
        return capacity;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public CenterGroupStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }
}
