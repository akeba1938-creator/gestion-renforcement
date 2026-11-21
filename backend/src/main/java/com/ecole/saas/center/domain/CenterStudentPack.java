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
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Souscription / carnet de pack de séances attribué à un élève (prix figé en FCFA entiers).
 */
@Entity
@Table(name = "center_student_packs")
public class CenterStudentPack extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "pack_id", nullable = false, updatable = false)
    private UUID packId;

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "pack_code_snapshot", nullable = false, length = 30)
    private String packCodeSnapshot;

    @Column(name = "pack_name_snapshot", nullable = false, length = 120)
    private String packNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 20)
    private CenterPackBillingMode billingMode;

    @Column(name = "price_xof", nullable = false)
    private long priceXof;

    @Column(name = "total_sessions")
    private Integer totalSessions;

    @Column(name = "consumed_sessions", nullable = false)
    private int consumedSessions = 0;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CenterStudentPackStatus status = CenterStudentPackStatus.ACTIVE;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CenterStudentPack() {
    }

    public CenterStudentPack(
            UUID studentId,
            CenterSessionPack pack,
            UUID groupId,
            Long customPriceXof,
            Integer customTotalSessions,
            LocalDate startDate,
            LocalDate endDate,
            UUID assignedBy,
            String notes) {
        Objects.requireNonNull(pack, "Le forfait est obligatoire.");
        if (!pack.isActive()) {
            throw new CenterConflictException(
                    "center-pack-inactive",
                    "Forfait inactif",
                    "Un forfait désactivé ne peut pas être attribué à un élève.");
        }
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.packId = Objects.requireNonNull(pack.getId(), "L'identifiant du forfait est obligatoire.");
        this.groupId = groupId;
        this.packCodeSnapshot = pack.getCode();
        this.packNameSnapshot = pack.getName();
        this.billingMode = pack.getBillingMode();
        long resolvedPrice = customPriceXof != null ? customPriceXof : pack.getPriceXof();
        if (resolvedPrice < 0L) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Montant FCFA invalide",
                    "Le montant du pack élève en FCFA doit être un entier positif ou nul.");
        }
        this.priceXof = resolvedPrice;
        Integer resolvedSessions = customTotalSessions != null ? customTotalSessions : pack.getIncludedSessions();
        if (this.billingMode == CenterPackBillingMode.SESSION_PACK) {
            if (resolvedSessions == null || resolvedSessions < 1 || resolvedSessions > CenterSessionPack.MAX_SESSIONS) {
                throw new CenterValidationException(
                        "center-student-pack-invalid",
                        "Nombre de séances invalide",
                        "Un pack de séances élève doit compter entre 1 et " + CenterSessionPack.MAX_SESSIONS + " séances.");
            }
        } else if (resolvedSessions != null
                && (resolvedSessions < 1 || resolvedSessions > CenterSessionPack.MAX_SESSIONS)) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Plafond de séances invalide",
                    "Le plafond de séances doit être compris entre 1 et " + CenterSessionPack.MAX_SESSIONS + ".");
        }
        this.totalSessions = resolvedSessions;
        this.consumedSessions = 0;
        if (startDate == null) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Date de début obligatoire",
                    "La date de début du forfait élève est obligatoire.");
        }
        LocalDate resolvedEndDate = endDate;
        if (resolvedEndDate == null && pack.getDurationDays() != null) {
            resolvedEndDate = startDate.plusDays(pack.getDurationDays() - 1L);
        }
        if (resolvedEndDate != null && resolvedEndDate.isBefore(startDate)) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Période invalide",
                    "La date de fin du forfait élève ne peut pas être antérieure à sa date de début.");
        }
        this.startDate = startDate;
        this.endDate = resolvedEndDate;
        this.status = CenterStudentPackStatus.ACTIVE;
        this.assignedBy = Objects.requireNonNull(assignedBy, "L'acteur d'attribution est obligatoire.");
        this.notes = normalizeOptional(notes, 500);
    }

    public Integer getRemainingSessions() {
        if (totalSessions == null) {
            return null;
        }
        return Math.max(0, totalSessions - consumedSessions);
    }

    public boolean isValidOnDate(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (date.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !date.isAfter(endDate);
    }

    public void consumeOneSession(LocalDate sessionDate) {
        if (this.status == CenterStudentPackStatus.CANCELLED) {
            throw new CenterConflictException(
                    "center-pack-cancelled",
                    "Forfait annulé",
                    "Un forfait annulé ne peut pas être utilisé pour une séance.");
        }
        if (this.status == CenterStudentPackStatus.EXPIRED || (endDate != null && sessionDate.isAfter(endDate))) {
            this.status = CenterStudentPackStatus.EXPIRED;
            throw new CenterConflictException(
                    "center-pack-expired",
                    "Forfait expiré",
                    "Le forfait de l'élève est expiré à la date de la séance (" + sessionDate + ").");
        }
        if (sessionDate.isBefore(startDate)) {
            throw new CenterConflictException(
                    "center-pack-not-started",
                    "Forfait non encore actif",
                    "Le forfait de l'élève ne débute que le " + startDate + ".");
        }
        if (this.status == CenterStudentPackStatus.EXHAUSTED
                || (totalSessions != null && consumedSessions >= totalSessions)) {
            this.status = CenterStudentPackStatus.EXHAUSTED;
            throw new CenterConflictException(
                    "center-pack-exhausted",
                    "Forfait épuisé",
                    "Toutes les séances du pack (" + totalSessions + ") ont déjà été consommées.");
        }
        this.consumedSessions = Math.addExact(this.consumedSessions, 1);
        if (totalSessions != null && this.consumedSessions >= totalSessions) {
            this.status = CenterStudentPackStatus.EXHAUSTED;
        }
    }

    public void refundOneSession(LocalDate referenceDate) {
        if (this.consumedSessions <= 0) {
            return;
        }
        this.consumedSessions = this.consumedSessions - 1;
        if (this.status == CenterStudentPackStatus.EXHAUSTED) {
            if (endDate != null && referenceDate != null && referenceDate.isAfter(endDate)) {
                this.status = CenterStudentPackStatus.EXPIRED;
            } else {
                this.status = CenterStudentPackStatus.ACTIVE;
            }
        }
    }

    public void cancel(String reason) {
        if (this.status == CenterStudentPackStatus.CANCELLED) {
            throw new CenterConflictException(
                    "center-pack-already-cancelled",
                    "Forfait déjà annulé",
                    "Ce forfait élève est déjà annulé.");
        }
        if (reason == null || reason.isBlank()) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Motif obligatoire",
                    "Un motif est obligatoire pour annuler un forfait élève.");
        }
        this.status = CenterStudentPackStatus.CANCELLED;
        this.statusReason = reason.trim().length() > 255 ? reason.trim().substring(0, 255) : reason.trim();
    }

    private static String normalizeOptional(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() > maxLen) {
            throw new CenterValidationException(
                    "center-student-pack-invalid",
                    "Forfait élève invalide",
                    "La longueur maximale autorisée (" + maxLen + " caractères) est dépassée.");
        }
        return trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getPackId() {
        return packId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getPackCodeSnapshot() {
        return packCodeSnapshot;
    }

    public String getPackNameSnapshot() {
        return packNameSnapshot;
    }

    public CenterPackBillingMode getBillingMode() {
        return billingMode;
    }

    public long getPriceXof() {
        return priceXof;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public int getConsumedSessions() {
        return consumedSessions;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public CenterStudentPackStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public String getNotes() {
        return notes;
    }

    public long getVersion() {
        return version;
    }
}
