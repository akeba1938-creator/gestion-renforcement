package com.ecole.saas.identity.api;

import com.ecole.saas.identity.application.MfaEnrollmentResult;

public record MfaEnrollResponse(
        String secret,
        String otpauthUri,
        int digits,
        int periodSeconds) {

    public static MfaEnrollResponse from(MfaEnrollmentResult result) {
        return new MfaEnrollResponse(
                result.secret(),
                result.otpauthUri(),
                result.digits(),
                result.periodSeconds());
    }
}
