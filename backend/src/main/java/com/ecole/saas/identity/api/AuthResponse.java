package com.ecole.saas.identity.api;

import com.ecole.saas.identity.application.AuthOutcome;
import com.ecole.saas.identity.application.AuthStatus;

public record AuthResponse(
        AuthStatus status,
        String accessToken,
        String mfaToken,
        long expiresInSeconds,
        boolean mfaRequired,
        boolean mfaEnrollmentRequired,
        AuthenticatedUserResponse user) {

    public static AuthResponse from(AuthOutcome outcome) {
        return new AuthResponse(
                outcome.status(),
                outcome.accessToken(),
                outcome.mfaToken(),
                outcome.expiresInSeconds(),
                outcome.status() != AuthStatus.AUTHENTICATED,
                outcome.status() == AuthStatus.MFA_ENROLLMENT_REQUIRED,
                AuthenticatedUserResponse.from(outcome.user()));
    }
}
