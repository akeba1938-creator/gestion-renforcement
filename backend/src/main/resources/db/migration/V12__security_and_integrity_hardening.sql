-- V12: Durcissement sécurité, immutabilité, intégrité référentielle intra-tenant, verrouillage optimiste et indexation

-- ============================================================================
-- 1. IMMUTABILITÉ DE subscription_events (trigger BEFORE UPDATE OR DELETE)
-- ============================================================================
CREATE OR REPLACE FUNCTION prevent_subscription_event_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Journal d''événements d''abonnement immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_subscription_events_immutable
    BEFORE UPDATE OR DELETE ON subscription_events
    FOR EACH ROW EXECUTE FUNCTION prevent_subscription_event_immutable_mutation();

-- ============================================================================
-- 2. ROW-LEVEL SECURITY SUR LES TABLES SENSIBLES PLATEFORME
--    Inaccessibles dès qu'un contexte tenant est actif (current_tenant_id() IS NULL)
-- ============================================================================
ALTER TABLE platform_admins ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_admins FORCE ROW LEVEL SECURITY;
CREATE POLICY platform_only_platform_admins ON platform_admins
    USING (current_tenant_id() IS NULL)
    WITH CHECK (current_tenant_id() IS NULL);

ALTER TABLE platform_refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_refresh_tokens FORCE ROW LEVEL SECURITY;
CREATE POLICY platform_only_platform_refresh_tokens ON platform_refresh_tokens
    USING (current_tenant_id() IS NULL)
    WITH CHECK (current_tenant_id() IS NULL);

-- ============================================================================
-- 3. CLÉS ÉTRANGÈRES COMPOSITES INTRA-TENANT & CONTRAINTES MANQUANTES
-- ============================================================================
ALTER TABLE subscriptions
    ADD CONSTRAINT uq_subscriptions_tenant_id_id UNIQUE (tenant_id, id);

ALTER TABLE student_enrollments
    ADD CONSTRAINT uq_student_enrollments_tenant_id_id UNIQUE (tenant_id, id);

-- Remplacement des 3 FKs non composites héritées de V2/V3/V4
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS user_roles_user_id_fkey;
ALTER TABLE user_roles
    ADD CONSTRAINT fk_user_roles_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id) ON DELETE CASCADE;

ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id) ON DELETE CASCADE;

ALTER TABLE subscription_events DROP CONSTRAINT IF EXISTS subscription_events_subscription_id_fkey;
ALTER TABLE subscription_events
    ADD CONSTRAINT fk_subscription_events_subscription
        FOREIGN KEY (tenant_id, subscription_id) REFERENCES subscriptions(tenant_id, id);

-- Ajout des 5 FKs composites intra-tenant manquantes (V6/V7/V11)
ALTER TABLE students
    ADD CONSTRAINT fk_students_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id);

ALTER TABLE guardians
    ADD CONSTRAINT fk_guardians_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id);

ALTER TABLE student_invoices
    ADD CONSTRAINT fk_student_invoices_enrollment
        FOREIGN KEY (tenant_id, enrollment_id) REFERENCES student_enrollments(tenant_id, id);

ALTER TABLE communication_deliveries
    ADD CONSTRAINT fk_communication_deliveries_recipient_user
        FOREIGN KEY (tenant_id, recipient_user_id) REFERENCES users(tenant_id, id);

ALTER TABLE communication_deliveries
    ADD CONSTRAINT fk_communication_deliveries_related_student
        FOREIGN KEY (tenant_id, related_student_id) REFERENCES students(tenant_id, id);

-- ============================================================================
-- 4. MOINDRE PRIVILÈGE ecole_app : RÉVOCATION DE DELETE SUR LES 16 TABLES
--    À ARCHIVAGE LOGIQUE / HISTORIQUE (V2, V5, V6)
-- ============================================================================
REVOKE DELETE ON
    schools,
    subscription_plans,
    platform_admins,
    subscriptions,
    users,
    academic_years,
    evaluation_periods,
    grade_levels,
    subjects,
    class_rooms,
    class_subjects,
    students,
    student_enrollments,
    families,
    guardians,
    student_guardians
FROM ecole_app;

-- ============================================================================
-- 5. CONTRAINTES CHECK COMPLÉMENTAIRES
-- ============================================================================
ALTER TABLE user_roles
    ADD CONSTRAINT chk_user_roles_no_platform_role CHECK (role_code <> 'ADMIN_SAAS');

ALTER TABLE receipts
    ADD CONSTRAINT chk_receipts_payment_method CHECK (payment_method IN (
        'CASH',
        'CHECK',
        'BANK_TRANSFER',
        'MOBILE_MONEY_WAVE',
        'MOBILE_MONEY_OM',
        'MOBILE_MONEY_FREE',
        'OTHER'
    ));

ALTER TABLE payment_reversals
    ADD CONSTRAINT chk_payment_reversals_payment_method CHECK (payment_method IN (
        'CASH',
        'CHECK',
        'BANK_TRANSFER',
        'MOBILE_MONEY_WAVE',
        'MOBILE_MONEY_OM',
        'MOBILE_MONEY_FREE',
        'OTHER'
    ));

ALTER TABLE student_invoice_lines
    ADD CONSTRAINT chk_student_invoice_lines_category CHECK (category IN (
        'ENROLLMENT',
        'RE_ENROLLMENT',
        'TUITION',
        'EXAM',
        'UNIFORM',
        'TRANSPORT',
        'CANTEEN',
        'TUTORING_PACK',
        'OTHER'
    ));

ALTER TABLE period_report_cards
    ADD CONSTRAINT chk_period_report_cards_non_negative_averages CHECK (
        (general_average IS NULL OR general_average >= 0)
        AND (class_average IS NULL OR class_average >= 0)
        AND (min_class_average IS NULL OR min_class_average >= 0)
        AND (max_class_average IS NULL OR max_class_average >= 0)
        AND (total_weighted_score IS NULL OR total_weighted_score >= 0)
    );

ALTER TABLE period_subject_results
    ADD CONSTRAINT chk_period_subject_results_non_negative_averages CHECK (
        (average_score IS NULL OR average_score >= 0)
        AND (class_subject_average IS NULL OR class_subject_average >= 0)
    );

-- ============================================================================
-- 6. VERROUILLAGE OPTIMISTE (@Version) & SNAPSHOT TARIFAIRE SÉANCES CENTRE
-- ============================================================================
ALTER TABLE student_invoices
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE fee_adjustments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payment_reversal_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE cash_closings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE center_student_packs
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE center_sessions
    ADD COLUMN hourly_rate_xof_snapshot BIGINT,
    ADD CONSTRAINT chk_center_sessions_hourly_rate_snapshot
        CHECK (hourly_rate_xof_snapshot IS NULL OR hourly_rate_xof_snapshot >= 0);

-- ============================================================================
-- 7. INDEXATION PERFORMANCE & NETTOYAGE INDEX REDONDANT
-- ============================================================================
-- Index V2 subsumé par uq_subscriptions_open_per_tenant (V4)
DROP INDEX IF EXISTS uq_subscriptions_active_per_tenant;

CREATE INDEX idx_subscriptions_status_ends_at
    ON subscriptions (status, ends_at);

CREATE INDEX idx_user_roles_tenant_role_code
    ON user_roles (tenant_id, role_code);

CREATE INDEX idx_refresh_tokens_tenant_expires
    ON refresh_tokens (tenant_id, expires_at);

CREATE INDEX idx_payments_tenant_method_paid_at
    ON payments (tenant_id, payment_method, paid_at DESC);

CREATE INDEX idx_student_invoices_tenant_due_status
    ON student_invoices (tenant_id, due_date, status);

CREATE INDEX idx_student_invoices_tenant_payer_guardian
    ON student_invoices (tenant_id, payer_guardian_id)
    WHERE payer_guardian_id IS NOT NULL;

CREATE INDEX idx_evaluations_tenant_teacher
    ON evaluations (tenant_id, teacher_id)
    WHERE teacher_id IS NOT NULL;

CREATE INDEX idx_attendance_sessions_tenant_teacher_date
    ON attendance_sessions (tenant_id, teacher_id, session_date DESC)
    WHERE teacher_id IS NOT NULL;

CREATE INDEX idx_communication_campaigns_tenant_scheduled
    ON communication_campaigns (tenant_id, status, scheduled_at)
    WHERE status = 'SCHEDULED';

CREATE INDEX idx_communication_deliveries_tenant_queued
    ON communication_deliveries (tenant_id, status, queued_at)
    WHERE status = 'QUEUED';

CREATE INDEX idx_platform_audit_logs_severity_occurred
    ON platform_audit_logs (severity, occurred_at DESC);
