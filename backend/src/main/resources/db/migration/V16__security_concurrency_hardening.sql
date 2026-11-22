-- V16: Durcissement sécurité RLS (schools, subscription_plans), moindre privilège ecole_app,
--      immutabilité SQL des résultats matière sur bulletins verrouillés et verrouillage optimiste (@Version)

-- ============================================================================
-- 1. ROW-LEVEL SECURITY (RLS) FAIL-CLOSED SUR schools ET subscription_plans
-- ============================================================================
ALTER TABLE schools ENABLE ROW LEVEL SECURITY;
ALTER TABLE schools FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_or_platform_schools ON schools
    USING (current_tenant_id() IS NULL OR id = current_tenant_id())
    WITH CHECK (current_tenant_id() IS NULL OR id = current_tenant_id());

ALTER TABLE subscription_plans ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription_plans FORCE ROW LEVEL SECURITY;
CREATE POLICY subscription_plans_select_all ON subscription_plans
    FOR SELECT
    USING (TRUE);
CREATE POLICY subscription_plans_platform_insert ON subscription_plans
    FOR INSERT
    WITH CHECK (current_tenant_id() IS NULL);
CREATE POLICY subscription_plans_platform_update ON subscription_plans
    FOR UPDATE
    USING (current_tenant_id() IS NULL)
    WITH CHECK (current_tenant_id() IS NULL);
CREATE POLICY subscription_plans_platform_delete ON subscription_plans
    FOR DELETE
    USING (current_tenant_id() IS NULL);

-- ============================================================================
-- 2. MOINDRE PRIVILÈGE ecole_app : RÉVOCATION DE DELETE SUR LES JETONS REFRESH
-- ============================================================================
REVOKE DELETE ON refresh_tokens, platform_refresh_tokens FROM ecole_app;

-- ============================================================================
-- 3. IMMUTABILITÉ DES RÉSULTATS MATIÈRE D'UN BULLETIN VERROUILLÉ (LOCKED)
-- ============================================================================
CREATE OR REPLACE FUNCTION prevent_locked_report_card_subject_result_mutation()
RETURNS TRIGGER AS $$
DECLARE
    v_locked BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
          FROM period_report_cards rc
         WHERE rc.tenant_id = OLD.tenant_id
           AND rc.id = OLD.report_card_id
           AND rc.status = 'LOCKED'
    ) INTO v_locked;

    IF v_locked THEN
        RAISE EXCEPTION 'Les résultats matière d''un bulletin verrouillé (LOCKED) sont strictement immuables.'
            USING ERRCODE = '55000';
    END IF;

    IF TG_OP = 'UPDATE' AND (NEW.report_card_id <> OLD.report_card_id OR NEW.tenant_id <> OLD.tenant_id) THEN
        SELECT EXISTS (
            SELECT 1
              FROM period_report_cards rc
             WHERE rc.tenant_id = NEW.tenant_id
               AND rc.id = NEW.report_card_id
               AND rc.status = 'LOCKED'
        ) INTO v_locked;

        IF v_locked THEN
            RAISE EXCEPTION 'Impossible de rattacher un résultat matière à un bulletin verrouillé (LOCKED).'
                USING ERRCODE = '55000';
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_period_subject_results_immutable_when_locked
    BEFORE UPDATE OR DELETE ON period_subject_results
    FOR EACH ROW EXECUTE FUNCTION prevent_locked_report_card_subject_result_mutation();

-- ============================================================================
-- 4. VERROUILLAGE OPTIMISTE (@Version) SUR LES ENTITÉS À CONCURRENCE CRITIQUE
-- ============================================================================
ALTER TABLE subscriptions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE evaluations
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE student_grades
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE period_report_cards
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE attendance_sessions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE attendance_records
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ============================================================================
-- 5. COHÉRENCE SQL DES FACTURES ANNULÉES (balance_xof = 0 lorsque status = 'CANCELLED')
-- ============================================================================
ALTER TABLE student_invoices
    DROP CONSTRAINT chk_student_invoices_amounts,
    ADD CONSTRAINT chk_student_invoices_amounts CHECK (
        gross_amount_xof >= 0
        AND discount_amount_xof >= 0
        AND discount_amount_xof <= gross_amount_xof
        AND net_amount_xof = gross_amount_xof - discount_amount_xof
        AND paid_amount_xof >= 0
        AND paid_amount_xof <= net_amount_xof
        AND (
            (status = 'CANCELLED' AND balance_xof = 0)
            OR (status <> 'CANCELLED' AND balance_xof = net_amount_xof - paid_amount_xof)
        )
    );
