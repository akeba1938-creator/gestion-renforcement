package com.ecole.saas.shared.infrastructure.security;

public final class JwtClaims {

    public static final String CLAIM_TENANT_ID = "tenant_id";
    public static final String CLAIM_SUBJECT_TYPE = "subject_type";
    public static final String CLAIM_TOKEN_TYPE = "token_type";
    public static final String CLAIM_ROLES = "roles";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_MFA_PENDING = "mfa_pending";

    public static final String SUBJECT_TYPE_USER = "USER";
    public static final String SUBJECT_TYPE_PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static final String AUTHORITY_ACCESS = "TOKEN_ACCESS";
    public static final String AUTHORITY_MFA_PENDING = "MFA_PENDING";

    private JwtClaims() {
    }
}
