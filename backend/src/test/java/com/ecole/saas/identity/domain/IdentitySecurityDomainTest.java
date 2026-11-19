package com.ecole.saas.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdentitySecurityDomainTest {

    @Test
    void passwordPolicyShouldEnforceLengthBounds() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("   "))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("court1234"))
                .isInstanceOf(PasswordPolicyViolationException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(129)))
                .isInstanceOf(PasswordPolicyViolationException.class);

        PasswordPolicy.validate("MotDePasseValide2026!");
        PasswordPolicy.validate("a".repeat(128));
    }

    @Test
    void loginSecurityShouldLockAfterFiveConsecutiveFailuresAndUnlockAfterDuration() {
        LoginSecurity security = new LoginSecurity();
        Instant t0 = Instant.parse("2026-11-19T12:00:00Z");

        for (int i = 1; i <= 4; i++) {
            assertThat(security.registerFailure(t0)).isFalse();
            assertThat(security.isLocked(t0)).isFalse();
            assertThat(security.getFailedLoginAttempts()).isEqualTo(i);
        }

        assertThat(security.registerFailure(t0)).isTrue();
        assertThat(security.isLocked(t0)).isTrue();
        assertThat(security.isLocked(t0.plusSeconds(14 * 60))).isTrue();
        assertThat(security.isLocked(t0.plus(LoginLockPolicy.LOCK_DURATION))).isFalse();

        // Après expiration du verrou, un nouvel échec repart à 1
        assertThat(security.registerFailure(t0.plus(LoginLockPolicy.LOCK_DURATION).plusSeconds(1))).isFalse();
        assertThat(security.getFailedLoginAttempts()).isEqualTo(1);

        security.registerSuccess();
        assertThat(security.getFailedLoginAttempts()).isZero();
        assertThat(security.getLockedUntil()).isNull();
    }

    @Test
    void totpGeneratorAndBase32ShouldVerifyValidCodeAndRejectReplay() {
        byte[] secret = TotpGenerator.newSecret(new SecureRandom());
        String encoded = Base32.encode(secret);
        assertThat(Base32.decode(encoded)).isEqualTo(secret);

        Instant now = Instant.parse("2026-11-19T12:00:15Z");
        long currentStep = TotpGenerator.timeStep(now);
        String code = TotpGenerator.generate(secret, currentStep);
        assertThat(code).matches("\\d{6}");

        OptionalLong first = TotpGenerator.verify(secret, code, now, null);
        assertThat(first).isPresent();
        assertThat(first.getAsLong()).isEqualTo(currentStep);

        // Anti-rejeu : même pas de temps refusé
        assertThat(TotpGenerator.verify(secret, code, now, currentStep)).isEmpty();
        // Pas précédent refusé aussi si <= lastUsedStep
        String previousCode = TotpGenerator.generate(secret, currentStep - 1);
        assertThat(TotpGenerator.verify(secret, previousCode, now, currentStep)).isEmpty();
        // Pas suivant accepté dans la tolérance de +1
        String nextCode = TotpGenerator.generate(secret, currentStep + 1);
        assertThat(TotpGenerator.verify(secret, nextCode, now, currentStep)).hasValue(currentStep + 1);
        // Code invalide refusé
        assertThat(TotpGenerator.verify(secret, "abcdef", now, null)).isEmpty();
    }

    @Test
    void mfaPolicyShouldRequireMfaForSensitiveRolesAndPlatformAdmins() {
        assertThat(MfaPolicy.isRequiredForPlatformAdmin()).isTrue();
        assertThat(MfaPolicy.isRequiredForTenantUser(List.of(RoleCode.DIRECTOR), false)).isTrue();
        assertThat(MfaPolicy.isRequiredForTenantUser(List.of(RoleCode.PROMOTER), false)).isTrue();
        assertThat(MfaPolicy.isRequiredForTenantUser(List.of(RoleCode.CENTER_MANAGER), false)).isTrue();
        assertThat(MfaPolicy.isRequiredForTenantUser(List.of(RoleCode.SECRETARY), true)).isTrue();
        assertThat(MfaPolicy.isRequiredForTenantUser(List.of(RoleCode.SECRETARY, RoleCode.TEACHER), false)).isFalse();
    }

    @Test
    void refreshCookieCodecShouldEncodeDecodeAndRejectMalformedValues() {
        SecureRandom random = new SecureRandom();
        UUID tenantId = UUID.randomUUID();

        RefreshCookieCodec.EncodedRefreshCookie userCookie = RefreshCookieCodec.createForTenantUser(tenantId, random);
        Optional<RefreshCookieCodec.ParsedRefreshCookie> parsedUser = RefreshCookieCodec.decode(userCookie.rawValue());
        assertThat(parsedUser).isPresent();
        assertThat(parsedUser.get().subjectType()).isEqualTo(SubjectType.USER);
        assertThat(parsedUser.get().tenantId()).isEqualTo(tenantId);
        assertThat(parsedUser.get().tokenHash()).isEqualTo(userCookie.tokenHash()).hasSize(64);

        RefreshCookieCodec.EncodedRefreshCookie platformCookie = RefreshCookieCodec.createForPlatformAdmin(random);
        Optional<RefreshCookieCodec.ParsedRefreshCookie> parsedPlatform =
                RefreshCookieCodec.decode(platformCookie.rawValue());
        assertThat(parsedPlatform).isPresent();
        assertThat(parsedPlatform.get().subjectType()).isEqualTo(SubjectType.PLATFORM_ADMIN);
        assertThat(parsedPlatform.get().tenantId()).isNull();
        assertThat(parsedPlatform.get().tokenHash()).isEqualTo(platformCookie.tokenHash()).hasSize(64);

        assertThat(RefreshCookieCodec.decode(null)).isEmpty();
        assertThat(RefreshCookieCodec.decode("")).isEmpty();
        assertThat(RefreshCookieCodec.decode("u.not-a-uuid.abcdefghijklmnopqrstuvwxyz0123456789")).isEmpty();
        assertThat(RefreshCookieCodec.decode("u." + tenantId + ".tropcourt")).isEmpty();
        assertThat(RefreshCookieCodec.decode("x." + tenantId + ".abcdefghijklmnopqrstuvwxyz0123456789")).isEmpty();
        assertThat(RefreshCookieCodec.decode("p.tropcourt")).isEmpty();
    }

    @Test
    void accountRefShouldEnforceSubjectTypeTenantInvariants() {
        UUID accountId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        assertThat(AccountRef.user(accountId, tenantId).isPlatformAdmin()).isFalse();
        assertThat(AccountRef.platformAdmin(accountId).isPlatformAdmin()).isTrue();
        assertThatThrownBy(() -> new AccountRef(SubjectType.USER, accountId, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRef(SubjectType.PLATFORM_ADMIN, accountId, tenantId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
