-- V13: Module schooldocs (modèles de documents scolaires/administratifs, émission officielle
-- séquentielle idempotente, duplicatas/rééditions tracées, vérification d'authenticité par jeton opaque,
-- révocation, stockage binaire privé immuable sous RLS et journal d'événements en ajout seul)

-- 1. Extension non destructive des contraintes de catégorie d'audit pour le module schooldocs
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
    'AUDIT'
));

-- 2. Fonction trigger d'immutabilité (ajout seul) pour les contenus binaires et événements documentaires
CREATE OR REPLACE FUNCTION prevent_schooldocs_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table documentaire immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 3. Séquence atomique de numérotation officielle isolée par établissement et année
CREATE TABLE schooldocs_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schooldocs_sequences_tenant_year UNIQUE (tenant_id, sequence_year),
    CONSTRAINT chk_schooldocs_sequences_year CHECK (sequence_year >= 2000 AND sequence_year <= 2100),
    CONSTRAINT chk_schooldocs_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_schooldocs_sequences_tenant_id
    ON schooldocs_sequences (tenant_id);

-- 4. Modèles de documents scolaires et administratifs (archivage logique, frais éventuels en FCFA entiers)
CREATE TABLE schooldocs_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    document_type VARCHAR(40) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'fr',
    title_template VARCHAR(255) NOT NULL,
    body_template TEXT NOT NULL,
    footer_template VARCHAR(1000),
    default_validity_days INTEGER,
    fee_amount_xof BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_schooldocs_templates_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_schooldocs_templates_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_schooldocs_templates_type CHECK (document_type IN (
        'SCHOOLING_CERTIFICATE',
        'ENROLLMENT_ATTESTATION',
        'ATTENDANCE_ATTESTATION',
        'SUCCESS_ATTESTATION',
        'SUMMONS',
        'CUSTOM_ADMINISTRATIVE'
    )),
    CONSTRAINT chk_schooldocs_templates_title_non_empty CHECK (length(trim(title_template)) > 0),
    CONSTRAINT chk_schooldocs_templates_body_non_empty CHECK (length(trim(body_template)) > 0),
    CONSTRAINT chk_schooldocs_templates_validity_days CHECK (
        default_validity_days IS NULL OR (default_validity_days >= 1 AND default_validity_days <= 3650)
    ),
    CONSTRAINT chk_schooldocs_templates_fee CHECK (fee_amount_xof >= 0)
);

CREATE INDEX idx_schooldocs_templates_tenant_id
    ON schooldocs_templates (tenant_id);
CREATE INDEX idx_schooldocs_templates_tenant_type_active
    ON schooldocs_templates (tenant_id, document_type, active);
CREATE INDEX idx_schooldocs_templates_tenant_active
    ON schooldocs_templates (tenant_id, active, code);

-- 5. Documents officiels émis (originaux et duplicatas/rééditions tracées)
CREATE TABLE schooldocs_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    document_number VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    template_id UUID,
    document_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    original_document_id UUID,
    edition_number INTEGER NOT NULL DEFAULT 1,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE,
    reissue_reason VARCHAR(500),
    student_id UUID NOT NULL,
    enrollment_id UUID,
    guardian_id UUID,
    academic_year_id UUID,
    class_room_id UUID,
    title VARCHAR(255) NOT NULL,
    rendered_body TEXT NOT NULL,
    rendered_footer VARCHAR(1000),
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_name VARCHAR(200) NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    content_sha256 VARCHAR(64) NOT NULL,
    fee_amount_xof BIGINT NOT NULL DEFAULT 0,
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by UUID NOT NULL,
    valid_until TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    revocation_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schooldocs_documents_template
        FOREIGN KEY (tenant_id, template_id) REFERENCES schooldocs_templates(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_original
        FOREIGN KEY (tenant_id, original_document_id) REFERENCES schooldocs_documents(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_enrollment
        FOREIGN KEY (tenant_id, enrollment_id) REFERENCES student_enrollments(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_guardian
        FOREIGN KEY (tenant_id, guardian_id) REFERENCES guardians(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_schooldocs_documents_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT uq_schooldocs_documents_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_schooldocs_documents_tenant_number UNIQUE (tenant_id, document_number),
    CONSTRAINT uq_schooldocs_documents_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_schooldocs_documents_tenant_token UNIQUE (tenant_id, verification_token),
    CONSTRAINT uq_schooldocs_documents_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_schooldocs_documents_type CHECK (document_type IN (
        'SCHOOLING_CERTIFICATE',
        'ENROLLMENT_ATTESTATION',
        'ATTENDANCE_ATTESTATION',
        'SUCCESS_ATTESTATION',
        'SUMMONS',
        'CUSTOM_ADMINISTRATIVE'
    )),
    CONSTRAINT chk_schooldocs_documents_status CHECK (status IN ('ISSUED', 'REVOKED')),
    CONSTRAINT chk_schooldocs_documents_edition CHECK (
        (is_duplicate = FALSE AND original_document_id IS NULL AND edition_number = 1 AND reissue_reason IS NULL)
        OR (is_duplicate = TRUE AND original_document_id IS NOT NULL AND edition_number >= 2
            AND reissue_reason IS NOT NULL AND length(trim(reissue_reason)) >= 5)
    ),
    CONSTRAINT chk_schooldocs_documents_revocation CHECK (
        (status = 'ISSUED' AND revoked_at IS NULL AND revoked_by IS NULL AND revocation_reason IS NULL)
        OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_by IS NOT NULL
            AND revocation_reason IS NOT NULL AND length(trim(revocation_reason)) >= 5)
    ),
    CONSTRAINT chk_schooldocs_documents_file_size CHECK (file_size_bytes > 0 AND file_size_bytes <= 5242880),
    CONSTRAINT chk_schooldocs_documents_fee CHECK (fee_amount_xof >= 0),
    CONSTRAINT chk_schooldocs_documents_title_non_empty CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_schooldocs_documents_body_non_empty CHECK (length(trim(rendered_body)) > 0),
    CONSTRAINT chk_schooldocs_documents_valid_until CHECK (valid_until IS NULL OR valid_until > issued_at)
);

CREATE UNIQUE INDEX uq_schooldocs_documents_tenant_original_edition
    ON schooldocs_documents (tenant_id, original_document_id, edition_number)
    WHERE original_document_id IS NOT NULL;

CREATE INDEX idx_schooldocs_documents_tenant_id
    ON schooldocs_documents (tenant_id);
CREATE INDEX idx_schooldocs_documents_tenant_issued_at
    ON schooldocs_documents (tenant_id, issued_at DESC);
CREATE INDEX idx_schooldocs_documents_tenant_student
    ON schooldocs_documents (tenant_id, student_id, issued_at DESC);
CREATE INDEX idx_schooldocs_documents_tenant_guardian
    ON schooldocs_documents (tenant_id, guardian_id, issued_at DESC)
    WHERE guardian_id IS NOT NULL;
CREATE INDEX idx_schooldocs_documents_tenant_class_room
    ON schooldocs_documents (tenant_id, class_room_id, issued_at DESC)
    WHERE class_room_id IS NOT NULL;
CREATE INDEX idx_schooldocs_documents_tenant_academic_year
    ON schooldocs_documents (tenant_id, academic_year_id, issued_at DESC)
    WHERE academic_year_id IS NOT NULL;
CREATE INDEX idx_schooldocs_documents_tenant_type_status
    ON schooldocs_documents (tenant_id, document_type, status);
CREATE INDEX idx_schooldocs_documents_tenant_original
    ON schooldocs_documents (tenant_id, original_document_id, edition_number ASC)
    WHERE original_document_id IS NOT NULL;

-- 6. Stockage binaire privé strictement immuable (ajout seul, isolé par tenant + RLS)
CREATE TABLE schooldocs_document_contents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    document_id UUID NOT NULL,
    storage_object_key VARCHAR(300) NOT NULL,
    content_type VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schooldocs_contents_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES schooldocs_documents(tenant_id, id),
    CONSTRAINT uq_schooldocs_contents_tenant_document UNIQUE (tenant_id, document_id),
    CONSTRAINT uq_schooldocs_contents_tenant_storage_key UNIQUE (tenant_id, storage_object_key),
    CONSTRAINT chk_schooldocs_contents_size CHECK (
        file_size_bytes > 0 AND file_size_bytes <= 5242880 AND octet_length(content) = file_size_bytes
    )
);

CREATE INDEX idx_schooldocs_contents_tenant_id
    ON schooldocs_document_contents (tenant_id);

CREATE TRIGGER trg_schooldocs_document_contents_immutable
    BEFORE UPDATE OR DELETE ON schooldocs_document_contents
    FOR EACH ROW EXECUTE FUNCTION prevent_schooldocs_immutable_mutation();

-- 7. Journal chronologique strictement immuable (ajout seul) des événements documentaires
CREATE TABLE schooldocs_document_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    document_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    related_document_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_schooldocs_events_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES schooldocs_documents(tenant_id, id),
    CONSTRAINT fk_schooldocs_events_related_document
        FOREIGN KEY (tenant_id, related_document_id) REFERENCES schooldocs_documents(tenant_id, id),
    CONSTRAINT uq_schooldocs_events_tenant_doc_seq UNIQUE (tenant_id, document_id, sequence_no),
    CONSTRAINT chk_schooldocs_events_seq CHECK (sequence_no > 0),
    CONSTRAINT chk_schooldocs_events_type CHECK (event_type IN ('ISSUED', 'DUPLICATE_ISSUED', 'REVOKED'))
);

CREATE INDEX idx_schooldocs_events_tenant_id
    ON schooldocs_document_events (tenant_id);
CREATE INDEX idx_schooldocs_events_tenant_document
    ON schooldocs_document_events (tenant_id, document_id, sequence_no ASC);

CREATE TRIGGER trg_schooldocs_document_events_immutable
    BEFORE UPDATE OR DELETE ON schooldocs_document_events
    FOR EACH ROW EXECUTE FUNCTION prevent_schooldocs_immutable_mutation();

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 5 TABLES
-- ============================================================================

ALTER TABLE schooldocs_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE schooldocs_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schooldocs_sequences ON schooldocs_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schooldocs_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE schooldocs_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schooldocs_templates ON schooldocs_templates
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schooldocs_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE schooldocs_documents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schooldocs_documents ON schooldocs_documents
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schooldocs_document_contents ENABLE ROW LEVEL SECURITY;
ALTER TABLE schooldocs_document_contents FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schooldocs_document_contents ON schooldocs_document_contents
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE schooldocs_document_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE schooldocs_document_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_schooldocs_document_events ON schooldocs_document_events
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS MOINDRE PRIVILÈGE DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    schooldocs_sequences,
    schooldocs_templates,
    schooldocs_documents
TO ecole_app;

GRANT SELECT, INSERT ON
    schooldocs_document_contents,
    schooldocs_document_events
TO ecole_app;
