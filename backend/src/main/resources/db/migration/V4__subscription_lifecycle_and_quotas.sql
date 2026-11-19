-- V4: Cycle de vie complet des abonnements, périodes d'essai, quotas effectifs et journal d'événements

-- 1. Enrichissement du catalogue global des forfaits (durée d'essai par défaut et description)
ALTER TABLE subscription_plans
    ADD COLUMN description TEXT,
    ADD COLUMN trial_days INTEGER NOT NULL DEFAULT 14;

ALTER TABLE subscription_plans
    ADD CONSTRAINT chk_subscription_plans_trial_days
        CHECK (trial_days >= 0 AND trial_days <= 365);

-- 2. Enrichissement des abonnements par établissement (essai, grâce, motif, extensions de quotas)
ALTER TABLE subscriptions
    ADD COLUMN trial_ends_at TIMESTAMPTZ,
    ADD COLUMN grace_period_ends_at TIMESTAMPTZ,
    ADD COLUMN suspended_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN status_reason VARCHAR(255),
    ADD COLUMN extra_students INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN extra_users INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN extra_storage_mb INTEGER NOT NULL DEFAULT 0;

ALTER TABLE subscriptions
    ADD CONSTRAINT chk_subscriptions_extra_quotas
        CHECK (extra_students >= 0 AND extra_users >= 0 AND extra_storage_mb >= 0),
    ADD CONSTRAINT chk_subscriptions_trial_ends_at
        CHECK (trial_ends_at IS NULL OR status <> 'TRIAL' OR (trial_ends_at > starts_at AND trial_ends_at <= ends_at)),
    ADD CONSTRAINT chk_subscriptions_grace_period
        CHECK (grace_period_ends_at IS NULL OR status <> 'PAST_DUE' OR grace_period_ends_at > starts_at);

-- Un seul abonnement ouvert (essai, actif ou impayé en cours de traitement) par établissement
CREATE UNIQUE INDEX uq_subscriptions_open_per_tenant
    ON subscriptions (tenant_id)
    WHERE status IN ('TRIAL', 'ACTIVE', 'PAST_DUE');

CREATE INDEX idx_subscriptions_tenant_created_at
    ON subscriptions (tenant_id, created_at DESC);

-- 3. Journal immuable (ajout seul) du cycle de vie des abonnements par établissement
CREATE TABLE subscription_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    amount_xof BIGINT NOT NULL,
    effective_max_students INTEGER NOT NULL,
    effective_max_users INTEGER NOT NULL,
    effective_max_storage_mb INTEGER NOT NULL,
    reason VARCHAR(255),
    actor_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_events_tenant_sub_seq UNIQUE (tenant_id, subscription_id, sequence_no),
    CONSTRAINT chk_subscription_events_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_subscription_events_type CHECK (event_type IN (
        'TRIAL_STARTED',
        'ACTIVATED',
        'RENEWED',
        'PLAN_CHANGED',
        'QUOTAS_ADJUSTED',
        'MARKED_PAST_DUE',
        'SUSPENDED',
        'REACTIVATED',
        'CANCELLED'
    )),
    CONSTRAINT chk_subscription_events_previous_status CHECK (
        previous_status IS NULL OR previous_status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')
    ),
    CONSTRAINT chk_subscription_events_new_status CHECK (
        new_status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')
    ),
    CONSTRAINT chk_subscription_events_amount CHECK (amount_xof >= 0),
    CONSTRAINT chk_subscription_events_quotas CHECK (
        effective_max_students > 0 AND effective_max_users > 0 AND effective_max_storage_mb > 0
    )
);

CREATE INDEX idx_subscription_events_tenant_id
    ON subscription_events (tenant_id);

CREATE INDEX idx_subscription_events_tenant_sub
    ON subscription_events (tenant_id, subscription_id, sequence_no DESC);

-- 4. Row-Level Security (RLS) activée et forcée sur subscription_events (fail-closed)
ALTER TABLE subscription_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_subscription_events ON subscription_events
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 5. Droits du rôle applicatif : journal en ajout seul (SELECT et INSERT uniquement)
GRANT SELECT, INSERT ON subscription_events TO ecole_app;
