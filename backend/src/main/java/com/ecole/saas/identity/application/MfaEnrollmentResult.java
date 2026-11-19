package com.ecole.saas.identity.application;

public record MfaEnrollmentResult(
        String secret,
        String otpauthUri,
        int digits,
        int periodSeconds) {
}
