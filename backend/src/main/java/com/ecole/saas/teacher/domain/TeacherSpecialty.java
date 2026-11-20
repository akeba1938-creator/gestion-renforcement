package com.ecole.saas.teacher.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "teacher_specialties")
public class TeacherSpecialty extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "teacher_id", nullable = false, updatable = false)
    private UUID teacherId;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "primary_specialty", nullable = false)
    private boolean primarySpecialty;

    @Column(name = "notes", length = 255)
    private String notes;

    protected TeacherSpecialty() {
    }

    public TeacherSpecialty(UUID teacherId, UUID subjectId, boolean primarySpecialty, String notes) {
        this.teacherId = Objects.requireNonNull(teacherId, "L'enseignant est obligatoire.");
        this.subjectId = Objects.requireNonNull(subjectId, "La matière est obligatoire.");
        this.primarySpecialty = primarySpecialty;
        this.notes = normalizeNotes(notes);
    }

    public void markPrimary(boolean primarySpecialty) {
        this.primarySpecialty = primarySpecialty;
    }

    public void updateNotes(String notes) {
        this.notes = normalizeNotes(notes);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public boolean isPrimarySpecialty() {
        return primarySpecialty;
    }

    public String getNotes() {
        return notes;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String trimmed = notes.trim();
        if (trimmed.length() > 255) {
            throw new TeacherValidationException("Les notes de spécialité ne doivent pas dépasser 255 caractères.");
        }
        return trimmed;
    }
}
