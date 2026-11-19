package com.ecole.saas.identity.domain;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new PasswordPolicyViolationException("Le mot de passe est obligatoire.");
        }
        int length = rawPassword.codePointCount(0, rawPassword.length());
        if (length < MIN_LENGTH) {
            throw new PasswordPolicyViolationException(
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (length > MAX_LENGTH) {
            throw new PasswordPolicyViolationException(
                    "Le mot de passe ne doit pas dépasser " + MAX_LENGTH + " caractères.");
        }
    }
}
