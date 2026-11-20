package com.ecole.saas.family.api;

import com.ecole.saas.family.application.StudentGuardianService.StudentGuardianView;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.RelationshipType;
import com.ecole.saas.family.domain.StudentGuardianLink;
import com.ecole.saas.student.StudentSummary;
import java.time.Instant;
import java.util.UUID;

public record StudentGuardianResponse(
        UUID id,
        UUID studentId,
        String studentMatricule,
        String studentFirstName,
        String studentLastName,
        UUID guardianId,
        UUID familyId,
        String guardianFirstName,
        String guardianLastName,
        String guardianPhone,
        String guardianEmail,
        RelationshipType relationshipType,
        boolean primaryContact,
        boolean financialResponsible,
        boolean legalCustody,
        boolean emergencyContact,
        boolean authorizedPickup,
        String notes,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static StudentGuardianResponse from(StudentGuardianView view) {
        StudentGuardianLink link = view.link();
        Guardian guardian = view.guardian();
        StudentSummary student = view.student();
        return new StudentGuardianResponse(
                link.getId(),
                link.getStudentId(),
                student != null ? student.matricule() : null,
                student != null ? student.firstName() : null,
                student != null ? student.lastName() : null,
                link.getGuardianId(),
                guardian != null ? guardian.getFamilyId() : null,
                guardian != null ? guardian.getFirstName() : null,
                guardian != null ? guardian.getLastName() : null,
                guardian != null ? guardian.getPhone() : null,
                guardian != null ? guardian.getEmail() : null,
                link.getRelationshipType(),
                link.isPrimaryContact(),
                link.isFinancialResponsible(),
                link.isLegalCustody(),
                link.isEmergencyContact(),
                link.isAuthorizedPickup(),
                link.getNotes(),
                link.isActive(),
                link.getCreatedAt(),
                link.getUpdatedAt());
    }
}
