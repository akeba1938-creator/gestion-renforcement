package com.ecole.saas.identity.application;

import com.ecole.saas.identity.domain.SubjectType;
import java.util.List;
import java.util.UUID;

public record UserProfile(
        UUID id,
        SubjectType subjectType,
        String email,
        String firstName,
        String lastName,
        List<String> roles,
        boolean mfaEnabled,
        UUID tenantId,
        String schoolCode,
        String schoolName) {
}
