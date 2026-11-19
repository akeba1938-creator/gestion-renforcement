package com.ecole.saas.identity.domain;

import java.time.Duration;

public final class LoginLockPolicy {

    public static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LoginLockPolicy() {
    }
}
