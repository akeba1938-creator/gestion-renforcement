package com.ecole.saas.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class JwtAuthenticationConverterTest {

    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    @Test
    void shouldAcceptValidTenantUserAndPlatformAdminTokens() {
        Jwt userJwt = buildJwt(JwtClaims.SUBJECT_TYPE_USER, UUID.randomUUID(), List.of("DIRECTOR", "CENSOR"));
        AbstractAuthenticationToken userAuth = converter.convert(userJwt);
        assertThat(userAuth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(JwtClaims.AUTHORITY_ACCESS, "ROLE_DIRECTOR", "ROLE_CENSOR");

        Jwt platformJwt = buildJwt(JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN, null, List.of("ADMIN_SAAS"));
        AbstractAuthenticationToken platformAuth = converter.convert(platformJwt);
        assertThat(platformAuth.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(JwtClaims.AUTHORITY_ACCESS, "ROLE_ADMIN_SAAS");
    }

    @Test
    void shouldRejectTenantUserCarryingAdminSaasRole() {
        Jwt forgedUserJwt = buildJwt(JwtClaims.SUBJECT_TYPE_USER, UUID.randomUUID(), List.of("DIRECTOR", "ADMIN_SAAS"));
        assertThatThrownBy(() -> converter.convert(forgedUserJwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void shouldRejectPlatformAdminCarryingTenantRole() {
        Jwt forgedPlatformJwt = buildJwt(JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN, null, List.of("DIRECTOR"));
        assertThatThrownBy(() -> converter.convert(forgedPlatformJwt))
                .isInstanceOf(InvalidBearerTokenException.class);

        Jwt mixedPlatformJwt = buildJwt(JwtClaims.SUBJECT_TYPE_PLATFORM_ADMIN, null, List.of("ADMIN_SAAS", "CASHIER"));
        assertThatThrownBy(() -> converter.convert(mixedPlatformJwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private static Jwt buildJwt(String subjectType, UUID tenantId, List<String> roles) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", UUID.randomUUID().toString());
        claims.put(JwtClaims.CLAIM_TOKEN_TYPE, JwtClaims.TOKEN_TYPE_ACCESS);
        claims.put(JwtClaims.CLAIM_SUBJECT_TYPE, subjectType);
        claims.put(JwtClaims.CLAIM_ROLES, roles);
        if (tenantId != null) {
            claims.put(JwtClaims.CLAIM_TENANT_ID, tenantId.toString());
        }
        return new Jwt("token-value", now, now.plusSeconds(900), Map.of("alg", "HS256"), claims);
    }
}
