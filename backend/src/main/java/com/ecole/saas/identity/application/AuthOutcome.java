package com.ecole.saas.identity.application;

public record AuthOutcome(
        AuthStatus status,
        String accessToken,
        String mfaToken,
        long expiresInSeconds,
        String refreshCookieValue,
        UserProfile user) {

    public static AuthOutcome authenticated(
            String accessToken,
            long expiresInSeconds,
            String refreshCookieValue,
            UserProfile user) {
        return new AuthOutcome(
                AuthStatus.AUTHENTICATED,
                accessToken,
                null,
                expiresInSeconds,
                refreshCookieValue,
                user);
    }

    public static AuthOutcome mfaChallenge(boolean enrollmentRequired, String mfaToken, long expiresInSeconds) {
        return new AuthOutcome(
                enrollmentRequired ? AuthStatus.MFA_ENROLLMENT_REQUIRED : AuthStatus.MFA_REQUIRED,
                null,
                mfaToken,
                expiresInSeconds,
                null,
                null);
    }
}
