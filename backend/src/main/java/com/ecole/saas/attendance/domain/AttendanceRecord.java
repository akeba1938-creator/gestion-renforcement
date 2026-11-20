package com.ecole.saas.attendance.domain;

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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends TenantScopedEntity {

    private static final Pattern SAFE_DOCUMENT_REF_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_.-]{0,511}$");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceRecordStatus status;

    @Column(name = "late_minutes")
    private Integer lateMinutes;

    @Column(name = "justified", nullable = false)
    private boolean justified;

    @Column(name = "justification_reason", length = 500)
    private String justificationReason;

    @Column(name = "justification_document_ref", length = 512)
    private String justificationDocumentRef;

    @Column(name = "justified_by")
    private UUID justifiedBy;

    @Column(name = "justified_at")
    private Instant justifiedAt;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(
            UUID sessionId,
            UUID studentId,
            AttendanceRecordStatus status,
            Integer lateMinutes,
            String notes,
            UUID recordedBy) {
        this.sessionId = Objects.requireNonNull(sessionId, "La séance d'appel est obligatoire.");
        this.studentId = Objects.requireNonNull(studentId, "L'élève est obligatoire.");
        this.recordedBy = Objects.requireNonNull(recordedBy, "L'auteur du pointage est obligatoire.");
        applyStatus(status, lateMinutes, notes);
    }

    public void updateRecord(AttendanceRecordStatus status, Integer lateMinutes, String notes, UUID updatedBy) {
        applyStatus(status, lateMinutes, notes);
        this.updatedBy = Objects.requireNonNull(updatedBy, "L'auteur de la mise à jour est obligatoire.");
    }

    public void justify(String reason, String documentRef, UUID actorId, Instant now) {
        if (this.status == AttendanceRecordStatus.PRESENT) {
            throw new AttendanceConflictException(
                    "attendance-record-not-justifiable",
                    "Pointage présent non justifiable",
                    "Un élève pointé PRESENT ne nécessite pas de justificatif d'absence ou de retard.");
        }
        if (reason == null || reason.isBlank()) {
            throw new AttendanceValidationException("Le motif de justification est obligatoire.");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new AttendanceValidationException("Le motif de justification ne doit pas dépasser 500 caractères.");
        }
        this.justified = true;
        this.justificationReason = trimmedReason;
        this.justificationDocumentRef = normalizeDocumentRef(documentRef);
        this.justifiedBy = Objects.requireNonNull(actorId, "L'auteur de la justification est obligatoire.");
        this.justifiedAt = Objects.requireNonNull(now, "L'horodatage de justification est obligatoire.");
        this.updatedBy = actorId;
    }

    private void applyStatus(AttendanceRecordStatus status, Integer lateMinutes, String notes) {
        Objects.requireNonNull(status, "Le statut d'assiduité est obligatoire.");
        if (status == AttendanceRecordStatus.LATE) {
            if (lateMinutes == null || lateMinutes <= 0 || lateMinutes > 300) {
                throw new AttendanceValidationException(
                        "Le nombre de minutes de retard est obligatoire et doit être compris entre 1 et 300.");
            }
            this.lateMinutes = lateMinutes;
        } else {
            if (lateMinutes != null) {
                throw new AttendanceValidationException(
                        "Le nombre de minutes de retard ne doit être renseigné que pour un statut LATE.");
            }
            this.lateMinutes = null;
        }

        this.status = status;
        if (status == AttendanceRecordStatus.PRESENT) {
            this.justified = false;
            this.justificationReason = null;
            this.justificationDocumentRef = null;
            this.justifiedBy = null;
            this.justifiedAt = null;
        } else if (status == AttendanceRecordStatus.EXCUSED) {
            this.justified = true;
        }
        this.notes = normalizeNotes(notes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public AttendanceRecordStatus getStatus() {
        return status;
    }

    public Integer getLateMinutes() {
        return lateMinutes;
    }

    public boolean isJustified() {
        return justified;
    }

    public String getJustificationReason() {
        return justificationReason;
    }

    public String getJustificationDocumentRef() {
        return justificationDocumentRef;
    }

    public UUID getJustifiedBy() {
        return justifiedBy;
    }

    public Instant getJustifiedAt() {
        return justifiedAt;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > 500) {
            throw new AttendanceValidationException("Les notes ne doivent pas dépasser 500 caractères.");
        }
        return trimmed;
    }

    private static String normalizeDocumentRef(String documentRef) {
        if (documentRef == null || documentRef.isBlank()) {
            return null;
        }
        String trimmed = documentRef.trim();
        if (trimmed.contains("..") || trimmed.startsWith("/") || !SAFE_DOCUMENT_REF_PATTERN.matcher(trimmed).matches()) {
            throw new AttendanceValidationException(
                    "La référence de pièce justificative doit être une clé d'objet privée valide.");
        }
        return trimmed;
    }
}
