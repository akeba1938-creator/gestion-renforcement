package com.ecole.saas.subscription;

/**
 * État d'accès effectif d'un établissement calculé à partir de son abonnement et de la date courante.
 */
public enum EffectiveAccessState {
    TRIAL_ACTIVE(true, true),
    ACTIVE(true, true),
    PAST_DUE_GRACE(true, true),
    EXPIRED(true, false),
    SUSPENDED(false, false),
    CANCELLED(false, false),
    MISSING(false, false);

    private final boolean readAllowed;
    private final boolean writeAllowed;

    EffectiveAccessState(boolean readAllowed, boolean writeAllowed) {
        this.readAllowed = readAllowed;
        this.writeAllowed = writeAllowed;
    }

    public boolean isReadAllowed() {
        return readAllowed;
    }

    public boolean isWriteAllowed() {
        return writeAllowed;
    }
}
