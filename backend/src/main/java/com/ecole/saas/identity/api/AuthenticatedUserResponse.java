package com.ecole.saas.identity.api;

import com.ecole.saas.identity.application.UserProfile;
import com.ecole.saas.identity.domain.SubjectType;
import java.util.List;
import java.util.UUID;

public record AuthenticatedUserResponse(
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

    public static AuthenticatedUserResponse from(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new AuthenticatedUserResponse(
                profile.id(),
                profile.subjectType(),
                profile.email(),
                profile.firstName(),
                profile.lastName(),
                profile.roles(),
                profile.mfaEnabled(),
                profile.tenantId(),
                profile.schoolCode(),
                profile.schoolName());
    }
}
