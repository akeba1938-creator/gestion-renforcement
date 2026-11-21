-- V11: Module communication (modèles multicanaux, préférences/consentement, campagnes/envois idempotents,
-- remises multicanaux, boîte de réception in-app et journal immuable des statuts de remise)

-- 1. Extension non destructive des contraintes de catégorie d'audit pour le module communication
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
    'AUDIT'
));

-- 2. Fonction trigger d'immutabilité pour le journal des événements de remise
CREATE OR REPLACE FUNCTION prevent_communication_event_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Journal des événements de remise immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 3. Séquence de génération des références de campagnes/messages isolée par établissement et année
CREATE TABLE communication_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_communication_sequences_tenant_year UNIQUE (tenant_id, sequence_year),
    CONSTRAINT chk_communication_sequences_year CHECK (sequence_year >= 2000 AND sequence_year <= 2100),
    CONSTRAINT chk_communication_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_communication_sequences_tenant_id
    ON communication_sequences (tenant_id);

-- 4. Modèles de messages multicanaux (archivage logique, coût unitaire estimé en entiers FCFA)
CREATE TABLE communication_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'fr',
    subject_template VARCHAR(255),
    body_template TEXT NOT NULL,
    estimated_unit_cost_xof BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_communication_templates_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_communication_templates_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_communication_templates_category CHECK (category IN (
        'GENERAL',
        'ABSENCE_ALERT',
        'PAYMENT_RECEIPT',
        'PAYMENT_DUE_REMINDER',
        'REPORT_CARD_PUBLISHED',
        'CENTER_SESSION',
        'SUMMONS',
        'ANNOUNCEMENT',
        'CUSTOM'
    )),
    CONSTRAINT chk_communication_templates_channel CHECK (channel IN ('IN_APP', 'SMS', 'WHATSAPP', 'EMAIL')),
    CONSTRAINT chk_communication_templates_cost CHECK (estimated_unit_cost_xof >= 0),
    CONSTRAINT chk_communication_templates_body_non_empty CHECK (length(trim(body_template)) > 0)
);

CREATE INDEX idx_communication_templates_tenant_id
    ON communication_templates (tenant_id);
CREATE INDEX idx_communication_templates_tenant_channel_active
    ON communication_templates (tenant_id, channel, active);
CREATE INDEX idx_communication_templates_tenant_category_active
    ON communication_templates (tenant_id, category, active);

-- 5. Préférences de contact et consentement par destinataire
CREATE TABLE communication_contact_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    contact_type VARCHAR(20) NOT NULL,
    contact_id UUID NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    marketing_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'fr',
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    consent_source VARCHAR(30) NOT NULL DEFAULT 'ADMIN_BACKOFFICE',
    consent_recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_communication_contact_prefs_tenant_target UNIQUE (tenant_id, contact_type, contact_id),
    CONSTRAINT chk_communication_contact_prefs_type CHECK (contact_type IN ('GUARDIAN', 'STUDENT', 'TEACHER', 'STAFF_USER')),
    CONSTRAINT chk_communication_contact_prefs_channel CHECK (preferred_channel IN ('IN_APP', 'SMS', 'WHATSAPP', 'EMAIL')),
    CONSTRAINT chk_communication_contact_prefs_source CHECK (consent_source IN (
        'ADMIN_BACKOFFICE',
        'SELF_SERVICE',
        'ENROLLMENT_FORM',
        'OPT_OUT_REQUEST'
    )),
    CONSTRAINT chk_communication_contact_prefs_quiet_hours CHECK (
        (quiet_hours_start IS NULL AND quiet_hours_end IS NULL)
        OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL AND quiet_hours_start <> quiet_hours_end)
    )
);

CREATE INDEX idx_communication_contact_prefs_tenant_id
    ON communication_contact_preferences (tenant_id);
CREATE INDEX idx_communication_contact_prefs_tenant_type
    ON communication_contact_preferences (tenant_id, contact_type);

-- 6. Campagnes, envois ciblés et notifications déclenchées (idempotents, coûts en entiers FCFA)
CREATE TABLE communication_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    reference VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    origin VARCHAR(30) NOT NULL,
    category VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template_id UUID,
    title VARCHAR(150) NOT NULL,
    audience_type VARCHAR(40) NOT NULL,
    audience_target_id UUID,
    guardian_targeting_mode VARCHAR(30) NOT NULL DEFAULT 'ALL_ACTIVE',
    subject VARCHAR(255),
    body TEXT NOT NULL,
    respect_marketing_opt_out BOOLEAN NOT NULL DEFAULT FALSE,
    scheduled_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_recipients INTEGER NOT NULL DEFAULT 0,
    queued_count INTEGER NOT NULL DEFAULT 0,
    sent_count INTEGER NOT NULL DEFAULT 0,
    delivered_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    read_count INTEGER NOT NULL DEFAULT 0,
    unit_cost_xof BIGINT NOT NULL DEFAULT 0,
    estimated_cost_xof BIGINT NOT NULL DEFAULT 0,
    actual_cost_xof BIGINT NOT NULL DEFAULT 0,
    source_module VARCHAR(40),
    source_resource_type VARCHAR(50),
    source_resource_id UUID,
    created_by UUID,
    dispatched_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancelled_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_communication_campaigns_template
        FOREIGN KEY (tenant_id, template_id) REFERENCES communication_templates(tenant_id, id),
    CONSTRAINT uq_communication_campaigns_tenant_ref UNIQUE (tenant_id, reference),
    CONSTRAINT uq_communication_campaigns_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uq_communication_campaigns_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_communication_campaigns_origin CHECK (origin IN ('CAMPAIGN_MANUAL', 'DIRECT_MESSAGE', 'SYSTEM_TRIGGER')),
    CONSTRAINT chk_communication_campaigns_category CHECK (category IN (
        'GENERAL',
        'ABSENCE_ALERT',
        'PAYMENT_RECEIPT',
        'PAYMENT_DUE_REMINDER',
        'REPORT_CARD_PUBLISHED',
        'CENTER_SESSION',
        'SUMMONS',
        'ANNOUNCEMENT',
        'CUSTOM'
    )),
    CONSTRAINT chk_communication_campaigns_channel CHECK (channel IN ('IN_APP', 'SMS', 'WHATSAPP', 'EMAIL')),
    CONSTRAINT chk_communication_campaigns_audience CHECK (audience_type IN (
        'GUARDIAN',
        'FAMILY',
        'STUDENT',
        'STUDENT_GUARDIANS',
        'CLASS_ROOM_GUARDIANS',
        'CLASS_ROOM_STUDENTS',
        'CENTER_GROUP_GUARDIANS',
        'CENTER_GROUP_STUDENTS',
        'TEACHER',
        'ALL_TEACHERS',
        'STAFF_USER',
        'ALL_STAFF',
        'ALL_GUARDIANS'
    )),
    CONSTRAINT chk_communication_campaigns_guardian_mode CHECK (guardian_targeting_mode IN (
        'ALL_ACTIVE',
        'PRIMARY_ONLY',
        'FINANCIAL_RESPONSIBLE_ONLY'
    )),
    CONSTRAINT chk_communication_campaigns_status CHECK (status IN (
        'DRAFT',
        'SCHEDULED',
        'QUEUED',
        'SENDING',
        'COMPLETED',
        'PARTIALLY_FAILED',
        'FAILED',
        'CANCELLED'
    )),
    CONSTRAINT chk_communication_campaigns_counts CHECK (
        total_recipients >= 0 AND queued_count >= 0 AND sent_count >= 0
        AND delivered_count >= 0 AND failed_count >= 0 AND read_count >= 0
    ),
    CONSTRAINT chk_communication_campaigns_costs CHECK (
        unit_cost_xof >= 0 AND estimated_cost_xof >= 0 AND actual_cost_xof >= 0
    ),
    CONSTRAINT chk_communication_campaigns_body_non_empty CHECK (length(trim(body)) > 0)
);

CREATE INDEX idx_communication_campaigns_tenant_id
    ON communication_campaigns (tenant_id);
CREATE INDEX idx_communication_campaigns_tenant_created
    ON communication_campaigns (tenant_id, created_at DESC);
CREATE INDEX idx_communication_campaigns_tenant_status
    ON communication_campaigns (tenant_id, status, created_at DESC);
CREATE INDEX idx_communication_campaigns_tenant_category
    ON communication_campaigns (tenant_id, category, created_at DESC);
CREATE INDEX idx_communication_campaigns_tenant_creator
    ON communication_campaigns (tenant_id, created_by, created_at DESC)
    WHERE created_by IS NOT NULL;

-- 7. Remises individuelles par destinataire et canal (alimente également la boîte de réception IN_APP)
CREATE TABLE communication_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    campaign_id UUID NOT NULL,
    recipient_type VARCHAR(20) NOT NULL,
    recipient_id UUID NOT NULL,
    recipient_user_id UUID,
    related_student_id UUID,
    recipient_display_name VARCHAR(200) NOT NULL,
    category VARCHAR(40) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    destination_address VARCHAR(255),
    rendered_subject VARCHAR(255),
    rendered_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    provider_name VARCHAR(60),
    provider_message_id VARCHAR(120),
    failure_code VARCHAR(40),
    failure_reason VARCHAR(500),
    unit_cost_xof BIGINT NOT NULL DEFAULT 0,
    charged_cost_xof BIGINT NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_communication_deliveries_campaign
        FOREIGN KEY (tenant_id, campaign_id) REFERENCES communication_campaigns(tenant_id, id),
    CONSTRAINT uq_communication_deliveries_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_communication_deliveries_tenant_campaign_recipient_channel
        UNIQUE (tenant_id, campaign_id, recipient_type, recipient_id, channel),
    CONSTRAINT chk_communication_deliveries_recipient_type CHECK (recipient_type IN ('GUARDIAN', 'STUDENT', 'TEACHER', 'STAFF_USER')),
    CONSTRAINT chk_communication_deliveries_category CHECK (category IN (
        'GENERAL',
        'ABSENCE_ALERT',
        'PAYMENT_RECEIPT',
        'PAYMENT_DUE_REMINDER',
        'REPORT_CARD_PUBLISHED',
        'CENTER_SESSION',
        'SUMMONS',
        'ANNOUNCEMENT',
        'CUSTOM'
    )),
    CONSTRAINT chk_communication_deliveries_channel CHECK (channel IN ('IN_APP', 'SMS', 'WHATSAPP', 'EMAIL')),
    CONSTRAINT chk_communication_deliveries_status CHECK (status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ')),
    CONSTRAINT chk_communication_deliveries_failure_code CHECK (
        failure_code IS NULL OR failure_code IN (
            'CHANNEL_OPTED_OUT',
            'MARKETING_OPTED_OUT',
            'MISSING_CONTACT_ADDRESS',
            'RECIPIENT_INACTIVE',
            'GATEWAY_REJECTED',
            'GATEWAY_ERROR'
        )
    ),
    CONSTRAINT chk_communication_deliveries_costs CHECK (unit_cost_xof >= 0 AND charged_cost_xof >= 0),
    CONSTRAINT chk_communication_deliveries_attempts CHECK (attempt_count >= 0),
    CONSTRAINT chk_communication_deliveries_body_non_empty CHECK (length(trim(rendered_body)) > 0)
);

CREATE UNIQUE INDEX uq_communication_deliveries_tenant_provider_msg
    ON communication_deliveries (tenant_id, provider_name, provider_message_id)
    WHERE provider_name IS NOT NULL AND provider_message_id IS NOT NULL;

CREATE INDEX idx_communication_deliveries_tenant_id
    ON communication_deliveries (tenant_id);
CREATE INDEX idx_communication_deliveries_tenant_campaign_status
    ON communication_deliveries (tenant_id, campaign_id, status);
CREATE INDEX idx_communication_deliveries_tenant_recipient
    ON communication_deliveries (tenant_id, recipient_type, recipient_id, created_at DESC);
CREATE INDEX idx_communication_deliveries_tenant_inbox_user
    ON communication_deliveries (tenant_id, recipient_user_id, created_at DESC)
    WHERE channel = 'IN_APP' AND recipient_user_id IS NOT NULL;
CREATE INDEX idx_communication_deliveries_tenant_inbox_unread
    ON communication_deliveries (tenant_id, recipient_user_id, status)
    WHERE channel = 'IN_APP' AND recipient_user_id IS NOT NULL;

-- 8. Journal strictement immuable (ajout seul) des transitions de statut de remise
CREATE TABLE communication_delivery_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    delivery_id UUID NOT NULL,
    sequence_no INTEGER NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    provider_name VARCHAR(60),
    provider_status VARCHAR(60),
    failure_code VARCHAR(40),
    detail VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_communication_delivery_events_delivery
        FOREIGN KEY (tenant_id, delivery_id) REFERENCES communication_deliveries(tenant_id, id),
    CONSTRAINT uq_communication_delivery_events_tenant_delivery_seq UNIQUE (tenant_id, delivery_id, sequence_no),
    CONSTRAINT chk_communication_delivery_events_seq CHECK (sequence_no > 0),
    CONSTRAINT chk_communication_delivery_events_prev_status CHECK (
        previous_status IS NULL OR previous_status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ')
    ),
    CONSTRAINT chk_communication_delivery_events_new_status CHECK (
        new_status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ')
    ),
    CONSTRAINT chk_communication_delivery_events_failure_code CHECK (
        failure_code IS NULL OR failure_code IN (
            'CHANNEL_OPTED_OUT',
            'MARKETING_OPTED_OUT',
            'MISSING_CONTACT_ADDRESS',
            'RECIPIENT_INACTIVE',
            'GATEWAY_REJECTED',
            'GATEWAY_ERROR'
        )
    )
);

CREATE INDEX idx_communication_delivery_events_tenant_id
    ON communication_delivery_events (tenant_id);
CREATE INDEX idx_communication_delivery_events_tenant_delivery
    ON communication_delivery_events (tenant_id, delivery_id, sequence_no ASC);

CREATE TRIGGER trg_communication_delivery_events_immutable
    BEFORE UPDATE OR DELETE ON communication_delivery_events
    FOR EACH ROW EXECUTE FUNCTION prevent_communication_event_mutation();

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 6 TABLES
-- ============================================================================

ALTER TABLE communication_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_sequences ON communication_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE communication_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_templates FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_templates ON communication_templates
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE communication_contact_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_contact_preferences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_contact_preferences ON communication_contact_preferences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE communication_campaigns ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_campaigns FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_campaigns ON communication_campaigns
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE communication_deliveries ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_deliveries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_deliveries ON communication_deliveries
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE communication_delivery_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE communication_delivery_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_communication_delivery_events ON communication_delivery_events
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    communication_sequences,
    communication_templates,
    communication_contact_preferences,
    communication_campaigns,
    communication_deliveries
TO ecole_app;

GRANT SELECT, INSERT ON
    communication_delivery_events
TO ecole_app;
