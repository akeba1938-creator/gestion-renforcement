package com.ecole.saas.family.domain;

import com.ecole.saas.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "student_guardians")
public class StudentGuardianLink extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "guardian_id", nullable = false, updatable = false)
    private UUID guardianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 30)
    private RelationshipType relationshipType;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "financial_responsible", nullable = false)
    private boolean financialResponsible;

    @Column(name = "legal_custody", nullable = false)
    private boolean legalCustody;

    @Column(name = "emergency_contact", nullable = false)
    private boolean emergencyContact;

    @Column(name = "authorized_pickup", nullable = false)
    private boolean authorizedPickup;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected StudentGuardianLink() {
    }

    public StudentGuardianLink(
            UUID studentId,
            UUID guardianId,
            RelationshipType relationshipType,
            boolean primaryContact,
            boolean financialResponsible,
            boolean legalCustody,
            boolean emergencyContact,
            boolean authorizedPickup,
            String notes) {
        this.studentId = Objects.requireNonNull(studentId, "L'identifiant de l'élève est obligatoire.");
        this.guardianId = Objects.requireNonNull(guardianId, "L'identifiant du parent ou tuteur est obligatoire.");
        this.relationshipType = Objects.requireNonNull(relationshipType, "Le lien de parenté est obligatoire.");
        this.primaryContact = primaryContact;
        this.financialResponsible = financialResponsible;
        this.legalCustody = legalCustody;
        this.emergencyContact = emergencyContact;
        this.authorizedPickup = authorizedPickup;
        this.notes = normalizeOptional(notes, 500, "Les observations");
        this.active = true;
    }

    public void update(
            RelationshipType relationshipType,
            boolean primaryContact,
            boolean financialResponsible,
            boolean legalCustody,
            boolean emergencyContact,
            boolean authorizedPickup,
            String notes) {
        if (!this.active) {
            throw new IllegalStateException("Un lien élève-tuteur désactivé ne peut pas être modifié avant réactivation.");
        }
        this.relationshipType = Objects.requireNonNull(relationshipType, "Le lien de parenté est obligatoire.");
        this.primaryContact = primaryContact;
        this.financialResponsible = financialResponsible;
        this.legalCustody = legalCustody;
        this.emergencyContact = emergencyContact;
        this.authorizedPickup = authorizedPickup;
        this.notes = normalizeOptional(notes, 500, "Les observations");
    }

    public void clearPrimaryContact() {
        this.primaryContact = false;
    }

    public void clearFinancialResponsible() {
        this.financialResponsible = false;
    }

    public void deactivate() {
        this.active = false;
        this.primaryContact = false;
        this.financialResponsible = false;
    }

    public void activate() {
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getGuardianId() {
        return guardianId;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public boolean isPrimaryContact() {
        return primaryContact;
    }

    public boolean isFinancialResponsible() {
        return financialResponsible;
    }

    public boolean isLegalCustody() {
        return legalCustody;
    }

    public boolean isEmergencyContact() {
        return emergencyContact;
    }

    public boolean isAuthorizedPickup() {
        return authorizedPickup;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
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
