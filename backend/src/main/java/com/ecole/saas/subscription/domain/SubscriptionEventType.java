package com.ecole.saas.subscription.domain;

public enum SubscriptionEventType {
    TRIAL_STARTED,
    ACTIVATED,
    RENEWED,
    PLAN_CHANGED,
    QUOTAS_ADJUSTED,
    MARKED_PAST_DUE,
    SUSPENDED,
    REACTIVATED,
    CANCELLED
}
