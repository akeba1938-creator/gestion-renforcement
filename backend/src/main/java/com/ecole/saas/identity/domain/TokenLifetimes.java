package com.ecole.saas.identity.domain;

import java.time.Duration;

public final class TokenLifetimes {

    public static final Duration ACCESS_TOKEN = Duration.ofMinutes(15);
    public static final Duration MFA_PENDING_TOKEN = Duration.ofMinutes(5);
    public static final Duration REFRESH_TOKEN = Duration.ofDays(7);

    private TokenLifetimes() {
    }
}
