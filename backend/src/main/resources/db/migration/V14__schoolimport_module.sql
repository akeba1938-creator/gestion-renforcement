-- V14: Module schoolimport (imports massifs de données scolaires CSV/XLSX, analyse à sec/prévisualisation,
-- validation stricte ligne par ligne, gestion des doublons/upsert contrôlé, idempotence, quotas d'abonnement,
-- stockage binaire privé immuable sous RLS et journal chronologique en ajout seul)

-- 1. Extension non destructive des contraintes de catégorie d'audit pour le module schoolimport
ALTER TABLE audit_logs DROP CONSTRAINT chk_audit_logs_category;
ALTER TABLE audit_logs ADD CONSTRAINT chk_audit_logs_category CHECK (category IN (
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
    'COMMUNICATION',
    'SCHOOLDOCS',
    'SCHOOLIMPORT',
    'AUDIT'
));

ALTER TABLE platform_audit_logs DROP CONSTRAINT chk_platform_audit_logs_category;
ALTER TABLE platform_audit_logs ADD CONSTRAINT chk_platform_audit_logs_category CHECK (category IN (
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
    'COMMUNICATION',
    'SCHOOLDOCS',
    'SCHOOLIMPORT',
    'AUDIT'
));

-- 2. Fonction trigger d'immutabilité (ajout seul) pour les fichiers importés et événements de lot
CREATE OR REPLACE FUNCTION prevent_schoolimport_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table schoolimport immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 3. Séquence atomique de numérotation officielle isolée par établissement et année
CREATE TABLE schoolimport_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schoolimport_sequences_tenant_year UNIQUE (tenant_id, sequence_year),
    CONSTRAINT chk_schoolimport_sequences_year CHECK (sequence_year >= 2000 AND sequence_year <= 2100),
    CONSTRAINT chk_schoolimport_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_schoolimport_sequences_tenant_id
    ON schoolimport_sequences (tenant_id);

-- 4. Lots d'import massif (analyse à sec, prévisualisation, exécution contrôlée et traçabilité complète)
CREATE TABLE schoolimport_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    job_number VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    execution_idempotency_key VARCHAR(128),
    import_type VARCHAR(30) NOT NULL,
    source_format VARCHAR(10) NOT NULL,
    validation_mode VARCHAR(30) NOT NULL DEFAULT 'STRICT_ALL_OR_NOTHING',
    duplicate_strategy VARCHAR(25) NOT NULL DEFAULT 'FAIL_ON_DUPLICATE',
    status VARCHAR(30) NOT NULL DEFAULT 'PREVIEW_READY',
    academic_year_id UUID,
    evaluation_id UUID,
    attendance_session_id UUID,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    duplicate_rows INTEGER NOT NULL DEFAULT 0,
    created_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    quota_resource VARCHAR(25),
    quota_limit BIGINT,
    quota_used_before BIGINT,
    quota_projected_after BIGINT,
    created_by UUID NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    executed_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancellation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schoolimport_jobs_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_schoolimport_jobs_evaluation
        FOREIGN KEY (tenant_id, evaluation_id) REFERENCES evaluations(tenant_id, id),
    CONSTRAINT fk_schoolimport_jobs_attendance_session
        FOREIGN KEY (tenant_id, attendance_session_id) REFERENCES attendance_sessions(tenant_id, id),
    CONSTRAINT uq_schoolimport_jobs_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_schoolimport_jobs_tenant_number UNIQUE (tenant_id, job_number),
    CONSTRAINT uq_schoolimport_jobs_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_schoolimport_jobs_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_schoolimport_jobs_type CHECK (import_type IN (
        'STUDENTS',
        'FAMILIES_GUARDIANS',
        'TEACHERS',
        'ENROLLMENTS',
        'GRADES',
        'ATTENDANCE'
    )),
    CONSTRAINT chk_schoolimport_jobs_format CHECK (source_format IN ('CSV', 'XLSX')),
    CONSTRAINT chk_schoolimport_jobs_validation_mode CHECK (validation_mode IN (
        'STRICT_ALL_OR_NOTHING',
        'PARTIAL_SKIP_ERRORS'
    )),
    CONSTRAINT chk_schoolimport_jobs_duplicate_strategy CHECK (duplicate_strategy IN (
        'FAIL_ON_DUPLICATE',
        'SKIP_DUPLICATES',
        'UPSERT_UPDATE'
    )),
    CONSTRAINT chk_schoolimport_jobs_status CHECK (status IN (
        'PREVIEW_READY',
        'VALIDATION_FAILED',
        'PROCESSING',
        'COMPLETED',
        'COMPLETED_WITH_ERRORS',
        'FAILED',
        'CANCELLED'
    )),
    CONSTRAINT chk_schoolimport_jobs_file_size CHECK (file_size_bytes > 0 AND file_size_bytes <= 5242880),
    CONSTRAINT chk_schoolimport_jobs_counts CHECK (
        total_rows >= 0 AND total_rows <= 2000
        AND valid_rows >= 0
        AND error_rows >= 0
        AND duplicate_rows >= 0
        AND created_count >= 0
        AND updated_count >= 0
        AND skipped_count >= 0
    ),
    CONSTRAINT chk_schoolimport_jobs_quota_resource CHECK (
        quota_resource IS NULL OR quota_resource IN ('STUDENTS', 'USERS', 'STORAGE_MB')
    ),
    CONSTRAINT chk_schoolimport_jobs_cancellation CHECK (
        (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
            AND cancellation_reason IS NOT NULL AND length(trim(cancellation_reason)) >= 5)
    )
);

CREATE UNIQUE INDEX uq_schoolimport_jobs_tenant_exec_idempotency
    ON schoolimport_jobs (tenant_id, execution_idempotency_key)
    WHERE execution_idempotency_key IS NOT NULL;

CREATE INDEX idx_schoolimport_jobs_tenant_id
    ON schoolimport_jobs (tenant_id);
CREATE INDEX idx_schoolimport_jobs_tenant_created_at
    ON schoolimport_jobs (tenant_id, created_at DESC);
CREATE INDEX idx_schoolimport_jobs_tenant_type_status
    ON schoolimport_jobs (tenant_id, import_type, status);
CREATE INDEX idx_schoolimport_jobs_tenant_created_by
    ON schoolimport_jobs (tenant_id, created_by, created_at DESC);
CREATE INDEX idx_schoolimport_jobs_tenant_academic_year
    ON schoolimport_jobs (tenant_id, academic_year_id)
    WHERE academic_year_id IS NOT NULL;

-- 5. Stockage binaire privé strictement immuable des fichiers téléversés (ajout seul + RLS fail-closed)
CREATE TABLE schoolimport_job_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    job_id UUID NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    detected_format VARCHAR(10) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schoolimport_files_job
        FOREIGN KEY (tenant_id, job_id) REFERENCES schoolimport_jobs(tenant_id, id),
    CONSTRAINT uq_schoolimport_files_tenant_job UNIQUE (tenant_id, job_id),
    CONSTRAINT uq_schoolimport_files_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_schoolimport_files_format CHECK (detected_format IN ('CSV', 'XLSX')),
    CONSTRAINT chk_schoolimport_files_size CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 5242880 AND octet_length(content) = file_size_bytes
    )
);

CREATE INDEX idx_schoolimport_files_tenant_id
    ON schoolimport_job_files (tenant_id);

CREATE TRIGGER trg_schoolimport_job_files_immutable
    BEFORE UPDATE OR DELETE ON schoolimport_job_files
    FOR EACH ROW EXECUTE FUNCTION prevent_schoolimport_immutable_mutation();

-- 6. Détail ligne par ligne d'un lot d'import (prévisualisation, erreurs détaillées et résultat d'exécution)
CREATE TABLE schoolimport_job_rows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    job_id UUID NOT NULL,
    row_number INTEGER NOT NULL,
    natural_key VARCHAR(200),
    row_status VARCHAR(25) NOT NULL,
    planned_action VARCHAR(25) NOT NULL,
    applied_action VARCHAR(25),
    error_code VARCHAR(60),
    error_field VARCHAR(80),
    error_message VARCHAR(600),
    target_entity_type VARCHAR(40),
    target_entity_id UUID,
    row_preview_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schoolimport_rows_job
        FOREIGN KEY (tenant_id, job_id) REFERENCES schoolimport_jobs(tenant_id, id),
    CONSTRAINT uq_schoolimport_rows_tenant_job_rownum UNIQUE (tenant_id, job_id, row_number),
    CONSTRAINT chk_schoolimport_rows_rownum CHECK (row_number >= 2),
    CONSTRAINT chk_schoolimport_rows_status CHECK (row_status IN (
        'VALID',
        'DUPLICATE',
        'ERROR',
        'CREATED',
        'UPDATED',
        'SKIPPED'
    )),
    CONSTRAINT chk_schoolimport_rows_planned_action CHECK (planned_action IN (
        'CREATE',
        'UPDATE',
        'SKIP_DUPLICATE',
        'ERROR'
    )),
    CONSTRAINT chk_schoolimport_rows_applied_action CHECK (
        applied_action IS NULL OR applied_action IN ('CREATE', 'UPDATE', 'SKIP_DUPLICATE', 'ERROR')
    )
);

CREATE INDEX idx_schoolimport_rows_tenant_id
    ON schoolimport_job_rows (tenant_id);
CREATE INDEX idx_schoolimport_rows_tenant_job_rownum
    ON schoolimport_job_rows (tenant_id, job_id, row_number ASC);
CREATE INDEX idx_schoolimport_rows_tenant_job_status
    ON schoolimport_job_rows (tenant_id, job_id, row_status, row_number ASC);

-- 7. Journal chronologique strictement immuable des événements de cycle de vie du lot d'import
CREATE TABLE schoolimport_job_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    job_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schoolimport_events_job
        FOREIGN KEY (tenant_id, job_id) REFERENCES schoolimport_jobs(tenant_id, id),
    CONSTRAINT uq_schoolimport_events_tenant_job_seq UNIQUE (tenant_id, job_id, sequence_no),
    CONSTRAINT chk_schoolimport_events_seq CHECK (sequence_no > 0),
    CONSTRAINT chk_schoolimport_events_type CHECK (event_type IN (
        'UPLOADED',
        'ANALYZED',
        'EXECUTION_STARTED',
        'COMPLETED',
        'COMPLETED_WITH_ERRORS',
        'FAILED',
        'CANCELLED'
    ))
);

CREATE INDEX idx_schoolimport_events_tenant_id
    ON schoolimport_job_events (tenant_id);
CREATE INDEX idx_schoolimport_events_tenant_job
    ON schoolimport_job_events (tenant_id, job_id, sequence_no ASC);

CREATE TRIGGER trg_schoolimport_job_events_immutable
    BEFORE UPDATE OR DELETE ON schoolimport_job_events
    FOR EACH ROW EXECUTE FUNCTION prevent_schoolimport_immutable_mutation();

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 5 TABLES
-- ============================================================================

ALTER TABLE schoolimport_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE schoolimport_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schoolimport_sequences ON schoolimport_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schoolimport_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE schoolimport_jobs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schoolimport_jobs ON schoolimport_jobs
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schoolimport_job_files ENABLE ROW LEVEL SECURITY;
ALTER TABLE schoolimport_job_files FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schoolimport_job_files ON schoolimport_job_files
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schoolimport_job_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE schoolimport_job_rows FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schoolimport_job_rows ON schoolimport_job_rows
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schoolimport_job_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE schoolimport_job_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schoolimport_job_events ON schoolimport_job_events
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS MOINDRE PRIVILÈGE DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    schoolimport_sequences,
    schoolimport_jobs,
    schoolimport_job_rows
TO ecole_app;

GRANT SELECT, INSERT ON
    schoolimport_job_files,
    schoolimport_job_events
TO ecole_app;
