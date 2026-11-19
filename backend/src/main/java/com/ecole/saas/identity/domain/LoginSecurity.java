package com.ecole.saas.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class LoginSecurity {

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret_encrypted")
    private String mfaSecretEncrypted;

    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    public LoginSecurity() {
        this.failedLoginAttempts = 0;
        this.mfaEnabled = false;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Enregistre un échec (mot de passe ou code MFA).
     *
     * @return {@code true} si cet échec vient de déclencher le verrouillage temporaire.
     */
    public boolean registerFailure(Instant now) {
        Objects.requireNonNull(now, "L'instant courant est obligatoire.");
        if (lockedUntil != null && !now.isBefore(lockedUntil)) {
            lockedUntil = null;
            failedLoginAttempts = 0;
        }
        failedLoginAttempts++;
        if (failedLoginAttempts >= LoginLockPolicy.MAX_CONSECUTIVE_FAILURES) {
            lockedUntil = now.plus(LoginLockPolicy.LOCK_DURATION);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccess() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void startMfaEnrollment(String encryptedSecret) {
        if (mfaEnabled) {
            throw new IllegalStateException("L'authentification multifacteur est déjà activée.");
        }
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalArgumentException("Le secret MFA chiffré est obligatoire.");
        }
        this.mfaSecretEncrypted = encryptedSecret;
        this.mfaLastUsedStep = null;
    }

    public void acceptMfaCode(long timeStep) {
        if (mfaSecretEncrypted == null) {
            throw new IllegalStateException("Aucun secret MFA n'est enregistré.");
        }
        if (mfaLastUsedStep != null && timeStep <= mfaLastUsedStep) {
            throw new IllegalStateException("Code MFA déjà utilisé.");
        }
        this.mfaEnabled = true;
        this.mfaLastUsedStep = timeStep;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public String getMfaSecretEncrypted() {
        return mfaSecretEncrypted;
    }

    public Long getMfaLastUsedStep() {
        return mfaLastUsedStep;
    }
}
