-- V15: Module reporting (synthèses consolidées par établissement/année/période/classe,
-- snapshots de rapports persistés et idempotents, stockage binaire privé immuable JSON/CSV
-- sous RLS fail-closed et journal chronologique d'événements en ajout seul)

-- 1. Extension non destructive des contraintes de catégorie d'audit pour le module reporting
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
    'REPORTING',
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
    'REPORTING',
    'AUDIT'
));

-- 2. Fonction trigger d'immutabilité (ajout seul) pour les contenus binaires et événements de snapshots
CREATE OR REPLACE FUNCTION prevent_reporting_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table reporting immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 3. Séquence atomique de numérotation officielle isolée par établissement et année
CREATE TABLE reporting_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reporting_sequences_tenant_year UNIQUE (tenant_id, sequence_year),
    CONSTRAINT chk_reporting_sequences_year CHECK (sequence_year >= 2000 AND sequence_year <= 2100),
    CONSTRAINT chk_reporting_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_reporting_sequences_tenant_id
    ON reporting_sequences (tenant_id);

-- 4. Snapshots persistés de rapports consolidés (idempotence, périmètre, archivage logique)
CREATE TABLE reporting_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    snapshot_number VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    report_type VARCHAR(30) NOT NULL,
    output_format VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    scope_type VARCHAR(20) NOT NULL,
    academic_year_id UUID,
    evaluation_period_id UUID,
    class_room_id UUID,
    student_id UUID,
    from_date DATE,
    to_date DATE,
    title VARCHAR(200) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    summary_json TEXT,
    generated_by UUID NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    archived_by UUID,
    archive_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reporting_snapshots_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_reporting_snapshots_evaluation_period
        FOREIGN KEY (tenant_id, evaluation_period_id) REFERENCES evaluation_periods(tenant_id, id),
    CONSTRAINT fk_reporting_snapshots_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_reporting_snapshots_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_reporting_snapshots_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_reporting_snapshots_tenant_number UNIQUE (tenant_id, snapshot_number),
    CONSTRAINT uq_reporting_snapshots_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_reporting_snapshots_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_reporting_snapshots_type CHECK (report_type IN (
        'EXECUTIVE',
        'ACADEMIC',
        'ATTENDANCE',
        'FINANCIAL',
        'OPERATIONS',
        'STUDENT_OVERVIEW'
    )),
    CONSTRAINT chk_reporting_snapshots_format CHECK (output_format IN ('JSON', 'CSV')),
    CONSTRAINT chk_reporting_snapshots_status CHECK (status IN ('READY', 'ARCHIVED')),
    CONSTRAINT chk_reporting_snapshots_scope CHECK (scope_type IN ('SCHOOL', 'CLASSROOM', 'STUDENT')),
    CONSTRAINT chk_reporting_snapshots_date_range CHECK (
        from_date IS NULL OR to_date IS NULL OR from_date <= to_date
    ),
    CONSTRAINT chk_reporting_snapshots_file_size CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 5242880
    ),
    CONSTRAINT chk_reporting_snapshots_archival CHECK (
        (status = 'READY' AND archived_at IS NULL AND archived_by IS NULL AND archive_reason IS NULL)
        OR (status = 'ARCHIVED' AND archived_at IS NOT NULL AND archived_by IS NOT NULL
            AND archive_reason IS NOT NULL AND length(trim(archive_reason)) >= 5)
    )
);

CREATE INDEX idx_reporting_snapshots_tenant_id
    ON reporting_snapshots (tenant_id);
CREATE INDEX idx_reporting_snapshots_tenant_generated_at
    ON reporting_snapshots (tenant_id, generated_at DESC);
CREATE INDEX idx_reporting_snapshots_tenant_type_status
    ON reporting_snapshots (tenant_id, report_type, status);
CREATE INDEX idx_reporting_snapshots_tenant_generated_by
    ON reporting_snapshots (tenant_id, generated_by, generated_at DESC);
CREATE INDEX idx_reporting_snapshots_tenant_academic_year
    ON reporting_snapshots (tenant_id, academic_year_id)
    WHERE academic_year_id IS NOT NULL;
CREATE INDEX idx_reporting_snapshots_tenant_student
    ON reporting_snapshots (tenant_id, student_id)
    WHERE student_id IS NOT NULL;

-- 5. Stockage binaire privé strictement immuable des snapshots (ajout seul + RLS fail-closed)
CREATE TABLE reporting_snapshot_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    snapshot_id UUID NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    output_format VARCHAR(10) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reporting_contents_snapshot
        FOREIGN KEY (tenant_id, snapshot_id) REFERENCES reporting_snapshots(tenant_id, id),
    CONSTRAINT uq_reporting_contents_tenant_snapshot UNIQUE (tenant_id, snapshot_id),
    CONSTRAINT uq_reporting_contents_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_reporting_contents_format CHECK (output_format IN ('JSON', 'CSV')),
    CONSTRAINT chk_reporting_contents_size CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 5242880 AND octet_length(content) = file_size_bytes
    )
);

CREATE INDEX idx_reporting_contents_tenant_id
    ON reporting_snapshot_contents (tenant_id);

CREATE TRIGGER trg_reporting_snapshot_contents_immutable
    BEFORE UPDATE OR DELETE ON reporting_snapshot_contents
    FOR EACH ROW EXECUTE FUNCTION prevent_reporting_immutable_mutation();

-- 6. Journal chronologique strictement immuable des événements de cycle de vie d'un snapshot
CREATE TABLE reporting_snapshot_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    snapshot_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reporting_events_snapshot
        FOREIGN KEY (tenant_id, snapshot_id) REFERENCES reporting_snapshots(tenant_id, id),
    CONSTRAINT uq_reporting_events_tenant_snapshot_seq UNIQUE (tenant_id, snapshot_id, sequence_no),
    CONSTRAINT chk_reporting_events_seq CHECK (sequence_no > 0),
    CONSTRAINT chk_reporting_events_type CHECK (event_type IN ('GENERATED', 'ARCHIVED'))
);

CREATE INDEX idx_reporting_events_tenant_id
    ON reporting_snapshot_events (tenant_id);
CREATE INDEX idx_reporting_events_tenant_snapshot
    ON reporting_snapshot_events (tenant_id, snapshot_id, sequence_no ASC);

CREATE TRIGGER trg_reporting_snapshot_events_immutable
    BEFORE UPDATE OR DELETE ON reporting_snapshot_events
    FOR EACH ROW EXECUTE FUNCTION prevent_reporting_immutable_mutation();

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 4 TABLES
-- ============================================================================

ALTER TABLE reporting_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE reporting_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reporting_sequences ON reporting_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE reporting_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE reporting_snapshots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reporting_snapshots ON reporting_snapshots
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE reporting_snapshot_contents ENABLE ROW LEVEL SECURITY;
ALTER TABLE reporting_snapshot_contents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reporting_snapshot_contents ON reporting_snapshot_contents
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE reporting_snapshot_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE reporting_snapshot_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_reporting_snapshot_events ON reporting_snapshot_events
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS MOINDRE PRIVILÈGE DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    reporting_sequences,
    reporting_snapshots
TO ecole_app;

GRANT SELECT, INSERT ON
    reporting_snapshot_contents,
    reporting_snapshot_events
TO ecole_app;
