-- V3: Jetons de renouvellement, anti-rejeu MFA et moindre privilège du rôle applicatif

-- 1. Anti-rejeu TOTP : dernier pas de temps accepté
ALTER TABLE users ADD COLUMN mfa_last_used_step BIGINT;
ALTER TABLE platform_admins ADD COLUMN mfa_last_used_step BIGINT;

-- 2. Jetons de renouvellement des utilisateurs d'établissement (table métier isolée par tenant)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_tenant_hash UNIQUE (tenant_id, token_hash)
);

CREATE INDEX idx_refresh_tokens_tenant_id ON refresh_tokens (tenant_id);
CREATE INDEX idx_refresh_tokens_tenant_family ON refresh_tokens (tenant_id, family_id);
CREATE INDEX idx_refresh_tokens_tenant_user ON refresh_tokens (tenant_id, user_id);

ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_refresh_tokens ON refresh_tokens
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 3. Jetons de renouvellement des administrateurs SaaS (hors établissement, sans tenant_id)
CREATE TABLE platform_refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES platform_admins(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_platform_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_platform_refresh_tokens_family ON platform_refresh_tokens (family_id);
CREATE INDEX idx_platform_refresh_tokens_admin ON platform_refresh_tokens (admin_id);

-- 4. Droits du rôle applicatif (soumis à la RLS, NOBYPASSRLS)
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens, platform_refresh_tokens TO ecole_app;

-- Le référentiel des rôles est en lecture seule pour l'application
REVOKE INSERT, UPDATE, DELETE ON roles FROM ecole_app;
