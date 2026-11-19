package com.ecole.saas.subscription.domain;

import java.util.EnumSet;
import java.util.Set;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    CANCELLED;

    public static final Set<SubscriptionStatus> OPEN_STATUSES =
            Set.copyOf(EnumSet.of(TRIAL, ACTIVE, PAST_DUE));

    public boolean isOpen() {
        return this == TRIAL || this == ACTIVE || this == PAST_DUE;
    }

    public boolean isTerminal() {
        return this == CANCELLED;
    }
}
