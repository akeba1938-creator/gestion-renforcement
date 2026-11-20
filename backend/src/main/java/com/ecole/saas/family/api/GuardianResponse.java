package com.ecole.saas.family.api;

import com.ecole.saas.family.application.GuardianService.GuardianView;
import com.ecole.saas.family.domain.Guardian;
import com.ecole.saas.family.domain.GuardianGender;
import com.ecole.saas.family.domain.GuardianStatus;
import java.time.Instant;
import java.util.UUID;

public record GuardianResponse(
        UUID id,
        UUID familyId,
        String familyCode,
        String familyName,
        String firstName,
        String lastName,
        GuardianGender gender,
        String phone,
        String secondaryPhone,
        String email,
        String profession,
        String address,
        String nationalIdNumber,
        String preferredLanguage,
        GuardianStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static GuardianResponse from(GuardianView view) {
        Guardian guardian = view.guardian();
        return new GuardianResponse(
                guardian.getId(),
                guardian.getFamilyId(),
                view.familyCode(),
                view.familyName(),
                guardian.getFirstName(),
                guardian.getLastName(),
                guardian.getGender(),
                guardian.getPhone(),
                guardian.getSecondaryPhone(),
                guardian.getEmail(),
                guardian.getProfession(),
                guardian.getAddress(),
                guardian.getNationalIdNumber(),
                guardian.getPreferredLanguage(),
                guardian.getStatus(),
                guardian.getArchivedAt(),
                guardian.getCreatedAt(),
                guardian.getUpdatedAt());
    }
}
