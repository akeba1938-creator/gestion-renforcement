-- V2: Modèle de données du socle et isolation multi-tenant (RLS PostgreSQL)

-- 1. Établissements (écoles privées et centres de renforcement)
CREATE TABLE schools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(150) NOT NULL,
    school_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Africa/Dakar',
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schools_code UNIQUE (code),
    CONSTRAINT chk_schools_type CHECK (school_type IN ('PRIVATE_SCHOOL', 'TUTORING_CENTER')),
    CONSTRAINT chk_schools_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

-- 2. Catalogue global des forfaits SaaS (montants en entiers FCFA / XOF)
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    monthly_price_xof BIGINT NOT NULL,
    yearly_price_xof BIGINT NOT NULL,
    max_students INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_storage_mb INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_plans_code UNIQUE (code),
    CONSTRAINT chk_subscription_plans_prices CHECK (monthly_price_xof >= 0 AND yearly_price_xof >= 0),
    CONSTRAINT chk_subscription_plans_quotas CHECK (max_students > 0 AND max_users > 0 AND max_storage_mb > 0)
);

-- 3. Référentiel des rôles de base (école privée + centre de renforcement + plateforme SaaS)
CREATE TABLE roles (
    code VARCHAR(30) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    platform_role BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO roles (code, label, platform_role) VALUES
    ('ADMIN_SAAS', 'Administrateur SaaS', TRUE),
    ('PROMOTER', 'Promoteur', FALSE),
    ('DIRECTOR', 'Directeur', FALSE),
    ('SECRETARY', 'Secrétariat', FALSE),
    ('CASHIER', 'Caissier', FALSE),
    ('CENSOR', 'Censeur', FALSE),
    ('TEACHER', 'Enseignant', FALSE),
    ('CENTER_MANAGER', 'Responsable de centre', FALSE),
    ('TUTOR', 'Intervenant', FALSE),
    ('PARENT', 'Parent', FALSE),
    ('STUDENT', 'Élève', FALSE);

-- 4. Administrateurs SaaS (hors établissement)
CREATE TABLE platform_admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret_encrypted TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_platform_admins_email UNIQUE (email),
    CONSTRAINT chk_platform_admins_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_platform_admins_failed_attempts CHECK (failed_login_attempts >= 0)
);

-- 5. Abonnements et quotas par établissement (table métier isolée par tenant)
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(20) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL,
    amount_xof BIGINT NOT NULL,
    max_students INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_storage_mb INTEGER NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_subscriptions_status CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    CONSTRAINT chk_subscriptions_cycle CHECK (billing_cycle IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT chk_subscriptions_amount CHECK (amount_xof >= 0),
    CONSTRAINT chk_subscriptions_quotas CHECK (max_students > 0 AND max_users > 0 AND max_storage_mb > 0),
    CONSTRAINT chk_subscriptions_dates CHECK (ends_at > starts_at)
);

CREATE INDEX idx_subscriptions_tenant_id ON subscriptions (tenant_id);
CREATE UNIQUE INDEX uq_subscriptions_active_per_tenant
    ON subscriptions (tenant_id)
    WHERE status IN ('TRIAL', 'ACTIVE');

-- 6. Utilisateurs d'un établissement (un utilisateur appartient à un seul établissement)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret_encrypted TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT chk_users_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_users_tenant_id ON users (tenant_id);

-- 7. Rôles attribués aux utilisateurs (isolés par tenant)
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_code VARCHAR(30) NOT NULL REFERENCES roles(code),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_roles_tenant_user_role UNIQUE (tenant_id, user_id, role_code)
);

CREATE INDEX idx_user_roles_tenant_id ON user_roles (tenant_id);
CREATE INDEX idx_user_roles_tenant_user ON user_roles (tenant_id, user_id);

-- 8. Années scolaires par établissement (isolées par tenant)
CREATE TABLE academic_years (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(20) NOT NULL,
    label VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_academic_years_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_academic_years_dates CHECK (end_date > start_date),
    CONSTRAINT chk_academic_years_status CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'))
);

CREATE INDEX idx_academic_years_tenant_id ON academic_years (tenant_id);
CREATE UNIQUE INDEX uq_academic_years_current_per_tenant
    ON academic_years (tenant_id)
    WHERE is_current = TRUE;

-- 9. Row-Level Security (RLS) activée et forcée sur toutes les tables métier (fail-closed)
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscriptions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_subscriptions ON subscriptions
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_users ON users
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_user_roles ON user_roles
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE academic_years ENABLE ROW LEVEL SECURITY;
ALTER TABLE academic_years FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_academic_years ON academic_years
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 10. Rôle applicatif non-superuser soumis strictement à la RLS (NOBYPASSRLS)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ecole_app') THEN
        CREATE ROLE ecole_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO ecole_app;
GRANT EXECUTE ON FUNCTION current_tenant_id() TO ecole_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    schools,
    subscription_plans,
    roles,
    platform_admins,
    subscriptions,
    users,
    user_roles,
    academic_years
TO ecole_app;
