-- V10: Module audit (journaux d'audit strictement immuables en ajout seul, isolation RLS établissement et séparation plateforme ADMIN_SAAS)

-- 1. Fonction trigger d'immutabilité (défense en profondeur contre tout UPDATE ou DELETE)
CREATE OR REPLACE FUNCTION prevent_audit_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Journal d''audit immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 2. Journal d'audit établissement (isolé par tenant_id, RLS activée et forcée en fail-closed, ajout seul)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    category VARCHAR(30) NOT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID,
    actor_roles VARCHAR(255),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    summary VARCHAR(500) NOT NULL,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_logs_category CHECK (category IN (
        'AUTH',
        'SECURITY',
        'SUBSCRIPTION',
        'SCHOOL',
        'ACADEMIC',
        'STUDENT',
        'FAMILY',
        'FINANCE',
        'TEACHER',
        'GRADE',
        'ATTENDANCE',
        'CENTER',
        'AUDIT'
    )),
    CONSTRAINT chk_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT chk_audit_logs_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_audit_logs_actor_type CHECK (actor_type IN ('USER', 'PLATFORM_ADMIN', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT chk_audit_logs_action_non_empty CHECK (length(trim(action)) > 0),
    CONSTRAINT chk_audit_logs_resource_type_non_empty CHECK (length(trim(resource_type)) > 0),
    CONSTRAINT chk_audit_logs_summary_non_empty CHECK (length(trim(summary)) > 0),
    CONSTRAINT chk_audit_logs_metadata_len CHECK (length(metadata_json) <= 8192)
);

CREATE INDEX idx_audit_logs_tenant_occurred
    ON audit_logs (tenant_id, occurred_at DESC, id DESC);
CREATE INDEX idx_audit_logs_tenant_category_occurred
    ON audit_logs (tenant_id, category, occurred_at DESC);
CREATE INDEX idx_audit_logs_tenant_action_occurred
    ON audit_logs (tenant_id, action, occurred_at DESC);
CREATE INDEX idx_audit_logs_tenant_actor_occurred
    ON audit_logs (tenant_id, actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;
CREATE INDEX idx_audit_logs_tenant_resource
    ON audit_logs (tenant_id, resource_type, resource_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_tenant_severity
    ON audit_logs (tenant_id, severity, occurred_at DESC);

-- 3. Journal d'audit plateforme ADMIN_SAAS (strictement séparé des données établissement, ajout seul)
CREATE TABLE platform_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(30) NOT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID,
    actor_roles VARCHAR(255),
    target_school_id UUID REFERENCES schools(id),
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    summary VARCHAR(500) NOT NULL,
    reason VARCHAR(500),
    metadata_json TEXT NOT NULL DEFAULT '{}',
    correlation_id VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_platform_audit_logs_category CHECK (category IN (
        'AUTH',
        'SECURITY',
        'SUBSCRIPTION',
        'SCHOOL',
        'ACADEMIC',
        'STUDENT',
        'FAMILY',
        'FINANCE',
        'TEACHER',
        'GRADE',
        'ATTENDANCE',
        'CENTER',
        'AUDIT'
    )),
    CONSTRAINT chk_platform_audit_logs_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT chk_platform_audit_logs_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_platform_audit_logs_actor_type CHECK (actor_type IN ('USER', 'PLATFORM_ADMIN', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT chk_platform_audit_logs_action_non_empty CHECK (length(trim(action)) > 0),
    CONSTRAINT chk_platform_audit_logs_resource_type_non_empty CHECK (length(trim(resource_type)) > 0),
    CONSTRAINT chk_platform_audit_logs_summary_non_empty CHECK (length(trim(summary)) > 0),
    CONSTRAINT chk_platform_audit_logs_metadata_len CHECK (length(metadata_json) <= 8192)
);

CREATE INDEX idx_platform_audit_logs_occurred
    ON platform_audit_logs (occurred_at DESC, id DESC);
CREATE INDEX idx_platform_audit_logs_category_occurred
    ON platform_audit_logs (category, occurred_at DESC);
CREATE INDEX idx_platform_audit_logs_action_occurred
    ON platform_audit_logs (action, occurred_at DESC);
CREATE INDEX idx_platform_audit_logs_target_school
    ON platform_audit_logs (target_school_id, occurred_at DESC)
    WHERE target_school_id IS NOT NULL;
CREATE INDEX idx_platform_audit_logs_actor
    ON platform_audit_logs (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

-- 4. Triggers d'immutabilité (aucun UPDATE ni DELETE autorisé)
CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_immutable_mutation();

CREATE TRIGGER trg_platform_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON platform_audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_immutable_mutation();

-- 5. Row-Level Security (RLS) activée et forcée
-- 5a. audit_logs : isolation stricte par tenant_id (fail-closed sans contexte tenant)
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_audit_logs ON audit_logs
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 5b. platform_audit_logs : strictement inaccessible dès qu'un contexte tenant est actif
ALTER TABLE platform_audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY platform_only_audit_logs ON platform_audit_logs
    USING (current_tenant_id() IS NULL)
    WITH CHECK (current_tenant_id() IS NULL);

-- 6. Droits du rôle applicatif ecole_app (NOBYPASSRLS) : ajout seul (SELECT et INSERT uniquement)
GRANT SELECT, INSERT ON audit_logs, platform_audit_logs TO ecole_app;
