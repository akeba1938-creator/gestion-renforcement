package com.ecole.saas.family;

import java.util.UUID;

public record GuardianSummary(
        UUID id,
        UUID familyId,
        String firstName,
        String lastName,
        String phone,
        String secondaryPhone,
        String email,
        String profession,
        String status,
        boolean active) {
}
