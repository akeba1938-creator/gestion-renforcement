-- V7: Module finance (catalogue de frais, facturation, échéanciers, remises/bourses, encaissements immuables, reçus, contre-passations, registre comptable et clôtures de caisse)

-- 1. Fonction trigger d'immutabilité (défense en profondeur contre tout UPDATE ou DELETE sur les tables financières en ajout seul)
CREATE OR REPLACE FUNCTION prevent_finance_immutable_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table financière immuable (%) : modification et suppression interdites.', TG_TABLE_NAME
        USING ERRCODE = '55000';
END;
$$ LANGUAGE plpgsql;

-- 2. Séquences atomiques isolées par établissement (numéros de facture, numéros de reçu sans trou, séquence du registre comptable)
CREATE TABLE finance_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_type VARCHAR(20) NOT NULL,
    sequence_year INTEGER NOT NULL,
    last_value BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_sequences_tenant_type_year UNIQUE (tenant_id, sequence_type, sequence_year),
    CONSTRAINT chk_finance_sequences_type CHECK (sequence_type IN ('INVOICE', 'RECEIPT', 'LEDGER')),
    CONSTRAINT chk_finance_sequences_year CHECK (sequence_year = 0 OR (sequence_year >= 2000 AND sequence_year <= 2100)),
    CONSTRAINT chk_finance_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_finance_sequences_tenant_id ON finance_sequences (tenant_id);

-- 3. Catalogue des frais par établissement et année scolaire (montants en FCFA entiers)
CREATE TABLE fee_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    grade_level_id UUID,
    code VARCHAR(32) NOT NULL,
    label VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    amount_xof BIGINT NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_fee_items_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_fee_items_grade_level
        FOREIGN KEY (tenant_id, grade_level_id) REFERENCES grade_levels(tenant_id, id),
    CONSTRAINT uq_fee_items_tenant_year_code UNIQUE (tenant_id, academic_year_id, code),
    CONSTRAINT uq_fee_items_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_fee_items_category CHECK (category IN (
        'ENROLLMENT',
        'RE_ENROLLMENT',
        'TUITION',
        'EXAM',
        'UNIFORM',
        'TRANSPORT',
        'CANTEEN',
        'TUTORING_PACK',
        'OTHER'
    )),
    CONSTRAINT chk_fee_items_frequency CHECK (frequency IN (
        'ONE_TIME',
        'MONTHLY',
        'TRIMESTER',
        'SEMESTER',
        'ANNUAL'
    )),
    CONSTRAINT chk_fee_items_amount CHECK (amount_xof >= 0)
);

CREATE INDEX idx_fee_items_tenant_id ON fee_items (tenant_id);
CREATE INDEX idx_fee_items_tenant_year ON fee_items (tenant_id, academic_year_id);
CREATE INDEX idx_fee_items_tenant_level ON fee_items (tenant_id, grade_level_id);

-- 4. Factures / dossiers de facturation par élève et année scolaire
CREATE TABLE student_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    invoice_number VARCHAR(64) NOT NULL,
    student_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    enrollment_id UUID,
    payer_guardian_id UUID,
    family_id UUID,
    title VARCHAR(150) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    gross_amount_xof BIGINT NOT NULL DEFAULT 0,
    discount_amount_xof BIGINT NOT NULL DEFAULT 0,
    net_amount_xof BIGINT NOT NULL DEFAULT 0,
    paid_amount_xof BIGINT NOT NULL DEFAULT 0,
    balance_xof BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    issue_date DATE,
    due_date DATE NOT NULL,
    issued_at TIMESTAMPTZ,
    issued_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancellation_reason VARCHAR(255),
    notes TEXT,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_student_invoices_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_student_invoices_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_student_invoices_payer_guardian
        FOREIGN KEY (tenant_id, payer_guardian_id) REFERENCES guardians(tenant_id, id),
    CONSTRAINT fk_student_invoices_family
        FOREIGN KEY (tenant_id, family_id) REFERENCES families(tenant_id, id),
    CONSTRAINT uq_student_invoices_tenant_number UNIQUE (tenant_id, invoice_number),
    CONSTRAINT uq_student_invoices_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_student_invoices_currency CHECK (currency = 'XOF'),
    CONSTRAINT chk_student_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT chk_student_invoices_amounts CHECK (
        gross_amount_xof >= 0
        AND discount_amount_xof >= 0
        AND discount_amount_xof <= gross_amount_xof
        AND net_amount_xof = gross_amount_xof - discount_amount_xof
        AND paid_amount_xof >= 0
        AND paid_amount_xof <= net_amount_xof
        AND balance_xof = net_amount_xof - paid_amount_xof
    )
);

CREATE INDEX idx_student_invoices_tenant_id ON student_invoices (tenant_id);
CREATE INDEX idx_student_invoices_tenant_student_year ON student_invoices (tenant_id, student_id, academic_year_id);
CREATE INDEX idx_student_invoices_tenant_family ON student_invoices (tenant_id, family_id);
CREATE INDEX idx_student_invoices_tenant_status ON student_invoices (tenant_id, status);

-- 5. Lignes de facturation (snapshot du tarif au moment de la facturation)
CREATE TABLE student_invoice_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    invoice_id UUID NOT NULL,
    fee_item_id UUID NOT NULL,
    line_order INTEGER NOT NULL,
    fee_code VARCHAR(32) NOT NULL,
    label VARCHAR(150) NOT NULL,
    category VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_amount_xof BIGINT NOT NULL,
    line_total_xof BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_student_invoice_lines_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_student_invoice_lines_fee_item
        FOREIGN KEY (tenant_id, fee_item_id) REFERENCES fee_items(tenant_id, id),
    CONSTRAINT uq_student_invoice_lines_tenant_invoice_order UNIQUE (tenant_id, invoice_id, line_order),
    CONSTRAINT chk_student_invoice_lines_order CHECK (line_order > 0),
    CONSTRAINT chk_student_invoice_lines_quantity CHECK (quantity > 0 AND quantity <= 1000),
    CONSTRAINT chk_student_invoice_lines_amounts CHECK (
        unit_amount_xof >= 0
        AND line_total_xof = unit_amount_xof * quantity
    )
);

CREATE INDEX idx_student_invoice_lines_tenant_id ON student_invoice_lines (tenant_id);
CREATE INDEX idx_student_invoice_lines_tenant_invoice ON student_invoice_lines (tenant_id, invoice_id);

-- 6. Échéanciers de paiement d'une facture
CREATE TABLE payment_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    invoice_id UUID NOT NULL,
    installment_no INTEGER NOT NULL,
    label VARCHAR(120) NOT NULL,
    due_date DATE NOT NULL,
    amount_xof BIGINT NOT NULL,
    paid_amount_xof BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_schedules_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT uq_payment_schedules_tenant_invoice_no UNIQUE (tenant_id, invoice_id, installment_no),
    CONSTRAINT uq_payment_schedules_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_payment_schedules_no CHECK (installment_no > 0),
    CONSTRAINT chk_payment_schedules_amounts CHECK (
        amount_xof >= 0
        AND paid_amount_xof >= 0
        AND paid_amount_xof <= amount_xof
    ),
    CONSTRAINT chk_payment_schedules_status CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'))
);

CREATE INDEX idx_payment_schedules_tenant_id ON payment_schedules (tenant_id);
CREATE INDEX idx_payment_schedules_tenant_invoice ON payment_schedules (tenant_id, invoice_id, installment_no);
CREATE INDEX idx_payment_schedules_tenant_due_status ON payment_schedules (tenant_id, due_date, status);

-- 7. Demandes et approbations de remises et bourses encadrées
CREATE TABLE fee_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    invoice_id UUID NOT NULL,
    student_id UUID NOT NULL,
    adjustment_type VARCHAR(20) NOT NULL,
    calculation_mode VARCHAR(20) NOT NULL,
    percentage_value INTEGER,
    requested_amount_xof BIGINT NOT NULL,
    resolved_amount_xof BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_fee_adjustments_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_fee_adjustments_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT chk_fee_adjustments_type CHECK (adjustment_type IN ('DISCOUNT', 'SCHOLARSHIP')),
    CONSTRAINT chk_fee_adjustments_mode CHECK (calculation_mode IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_fee_adjustments_percentage CHECK (
        (calculation_mode = 'FIXED_AMOUNT' AND percentage_value IS NULL)
        OR (calculation_mode = 'PERCENTAGE' AND percentage_value >= 1 AND percentage_value <= 100)
    ),
    CONSTRAINT chk_fee_adjustments_amounts CHECK (requested_amount_xof >= 0 AND resolved_amount_xof > 0),
    CONSTRAINT chk_fee_adjustments_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX idx_fee_adjustments_tenant_id ON fee_adjustments (tenant_id);
CREATE INDEX idx_fee_adjustments_tenant_invoice ON fee_adjustments (tenant_id, invoice_id);
CREATE INDEX idx_fee_adjustments_tenant_status ON fee_adjustments (tenant_id, status);

-- 8. Encaissements validés (TABLE STRICTEMENT IMMUABLE EN AJOUT SEUL : AUCUN UPDATE NI DELETE)
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    invoice_id UUID NOT NULL,
    student_id UUID NOT NULL,
    payer_guardian_id UUID,
    requested_schedule_id UUID,
    amount_xof BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    payment_method VARCHAR(30) NOT NULL,
    external_reference VARCHAR(120),
    confirmation_source VARCHAR(25) NOT NULL DEFAULT 'MANUAL_DESK',
    idempotency_key VARCHAR(128) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    recorded_by UUID NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payments_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_payments_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_payments_payer_guardian
        FOREIGN KEY (tenant_id, payer_guardian_id) REFERENCES guardians(tenant_id, id),
    CONSTRAINT fk_payments_requested_schedule
        FOREIGN KEY (tenant_id, requested_schedule_id) REFERENCES payment_schedules(tenant_id, id),
    CONSTRAINT uq_payments_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_payments_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_payments_amount CHECK (amount_xof > 0),
    CONSTRAINT chk_payments_currency CHECK (currency = 'XOF'),
    CONSTRAINT chk_payments_method CHECK (payment_method IN (
        'CASH',
        'CHECK',
        'BANK_TRANSFER',
        'MOBILE_MONEY_WAVE',
        'MOBILE_MONEY_OM',
        'MOBILE_MONEY_FREE',
        'OTHER'
    )),
    CONSTRAINT chk_payments_confirmation_source CHECK (confirmation_source IN ('MANUAL_DESK', 'VERIFIED_WEBHOOK'))
);

CREATE UNIQUE INDEX uq_payments_tenant_method_ext_ref
    ON payments (tenant_id, payment_method, external_reference)
    WHERE external_reference IS NOT NULL;

CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
CREATE INDEX idx_payments_tenant_invoice ON payments (tenant_id, invoice_id);
CREATE INDEX idx_payments_tenant_student ON payments (tenant_id, student_id);
CREATE INDEX idx_payments_tenant_paid_at ON payments (tenant_id, paid_at DESC);

-- 9. Imputations d'un paiement sur les échéances (TABLE STRICTEMENT IMMUABLE EN AJOUT SEUL)
CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    payment_id UUID NOT NULL,
    schedule_id UUID NOT NULL,
    amount_xof BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_allocations_payment
        FOREIGN KEY (tenant_id, payment_id) REFERENCES payments(tenant_id, id),
    CONSTRAINT fk_payment_allocations_schedule
        FOREIGN KEY (tenant_id, schedule_id) REFERENCES payment_schedules(tenant_id, id),
    CONSTRAINT uq_payment_allocations_tenant_payment_schedule UNIQUE (tenant_id, payment_id, schedule_id),
    CONSTRAINT chk_payment_allocations_amount CHECK (amount_xof > 0)
);

CREATE INDEX idx_payment_allocations_tenant_id ON payment_allocations (tenant_id);
CREATE INDEX idx_payment_allocations_tenant_payment ON payment_allocations (tenant_id, payment_id);
CREATE INDEX idx_payment_allocations_tenant_schedule ON payment_allocations (tenant_id, schedule_id);

-- 10. Reçus séquentiels sans trou et jeton QR opaque (TABLE STRICTEMENT IMMUABLE EN AJOUT SEUL)
CREATE TABLE receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    payment_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    student_id UUID NOT NULL,
    receipt_number VARCHAR(64) NOT NULL,
    verification_token VARCHAR(128) NOT NULL,
    amount_xof BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    payment_method VARCHAR(30) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_receipts_payment
        FOREIGN KEY (tenant_id, payment_id) REFERENCES payments(tenant_id, id),
    CONSTRAINT fk_receipts_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_receipts_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_receipts_tenant_payment UNIQUE (tenant_id, payment_id),
    CONSTRAINT uq_receipts_tenant_number UNIQUE (tenant_id, receipt_number),
    CONSTRAINT uq_receipts_tenant_token UNIQUE (tenant_id, verification_token),
    CONSTRAINT chk_receipts_amount CHECK (amount_xof > 0),
    CONSTRAINT chk_receipts_currency CHECK (currency = 'XOF')
);

CREATE INDEX idx_receipts_tenant_id ON receipts (tenant_id);
CREATE INDEX idx_receipts_tenant_student ON receipts (tenant_id, student_id);
CREATE INDEX idx_receipts_tenant_issued_at ON receipts (tenant_id, issued_at DESC);

-- 11. Demandes de contre-passation / annulation d'encaissement validé (workflow demande -> approbation)
CREATE TABLE payment_reversal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    payment_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    student_id UUID NOT NULL,
    amount_xof BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(25) NOT NULL DEFAULT 'PENDING_APPROVAL',
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_reversal_requests_payment
        FOREIGN KEY (tenant_id, payment_id) REFERENCES payments(tenant_id, id),
    CONSTRAINT fk_payment_reversal_requests_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_payment_reversal_requests_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_payment_reversal_requests_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_payment_reversal_requests_amount CHECK (amount_xof > 0),
    CONSTRAINT chk_payment_reversal_requests_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_payment_reversal_requests_pending_per_payment
    ON payment_reversal_requests (tenant_id, payment_id)
    WHERE status = 'PENDING_APPROVAL';

CREATE INDEX idx_payment_reversal_requests_tenant_id ON payment_reversal_requests (tenant_id);
CREATE INDEX idx_payment_reversal_requests_tenant_status ON payment_reversal_requests (tenant_id, status);

-- 12. Contre-passations validées d'encaissements (TABLE STRICTEMENT IMMUABLE EN AJOUT SEUL)
CREATE TABLE payment_reversals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    original_payment_id UUID NOT NULL,
    reversal_request_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    student_id UUID NOT NULL,
    amount_xof BIGINT NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    approved_by UUID NOT NULL,
    reversed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_payment_reversals_payment
        FOREIGN KEY (tenant_id, original_payment_id) REFERENCES payments(tenant_id, id),
    CONSTRAINT fk_payment_reversals_request
        FOREIGN KEY (tenant_id, reversal_request_id) REFERENCES payment_reversal_requests(tenant_id, id),
    CONSTRAINT fk_payment_reversals_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT fk_payment_reversals_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_payment_reversals_tenant_original_payment UNIQUE (tenant_id, original_payment_id),
    CONSTRAINT uq_payment_reversals_tenant_request UNIQUE (tenant_id, reversal_request_id),
    CONSTRAINT chk_payment_reversals_amount CHECK (amount_xof > 0)
);

CREATE INDEX idx_payment_reversals_tenant_id ON payment_reversals (tenant_id);
CREATE INDEX idx_payment_reversals_tenant_reversed_at ON payment_reversals (tenant_id, reversed_at DESC);

-- 13. Registre comptable immuable en ajout seul (AUCUN UPDATE NI DELETE)
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_no BIGINT NOT NULL,
    student_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount_xof BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'XOF',
    reference_type VARCHAR(30) NOT NULL,
    reference_id UUID NOT NULL,
    reversal_of_entry_id UUID,
    reason VARCHAR(500),
    actor_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_ledger_entries_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_ledger_entries_invoice
        FOREIGN KEY (tenant_id, invoice_id) REFERENCES student_invoices(tenant_id, id),
    CONSTRAINT uq_ledger_entries_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ledger_entries_reversal_of
        FOREIGN KEY (tenant_id, reversal_of_entry_id) REFERENCES ledger_entries(tenant_id, id),
    CONSTRAINT uq_ledger_entries_tenant_sequence UNIQUE (tenant_id, sequence_no),
    CONSTRAINT chk_ledger_entries_sequence CHECK (sequence_no > 0),
    CONSTRAINT chk_ledger_entries_type CHECK (entry_type IN (
        'CHARGE',
        'CHARGE_REVERSAL',
        'DISCOUNT_CREDIT',
        'SCHOLARSHIP_CREDIT',
        'PAYMENT_CREDIT',
        'PAYMENT_REVERSAL'
    )),
    CONSTRAINT chk_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_entries_amount CHECK (amount_xof > 0),
    CONSTRAINT chk_ledger_entries_currency CHECK (currency = 'XOF'),
    CONSTRAINT chk_ledger_entries_ref_type CHECK (reference_type IN (
        'INVOICE',
        'ADJUSTMENT',
        'PAYMENT',
        'PAYMENT_REVERSAL'
    ))
);

CREATE UNIQUE INDEX uq_ledger_entries_tenant_reversal_of
    ON ledger_entries (tenant_id, reversal_of_entry_id)
    WHERE reversal_of_entry_id IS NOT NULL;

CREATE INDEX idx_ledger_entries_tenant_id ON ledger_entries (tenant_id);
CREATE INDEX idx_ledger_entries_tenant_student_seq ON ledger_entries (tenant_id, student_id, sequence_no DESC);
CREATE INDEX idx_ledger_entries_tenant_invoice_seq ON ledger_entries (tenant_id, invoice_id, sequence_no ASC);
CREATE INDEX idx_ledger_entries_tenant_ref ON ledger_entries (tenant_id, reference_type, reference_id);

-- 14. Clôtures journalières de caisse avec écart constaté
CREATE TABLE cash_closings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    closing_date DATE NOT NULL,
    cash_in_xof BIGINT NOT NULL,
    cash_out_reversals_xof BIGINT NOT NULL,
    expected_cash_xof BIGINT NOT NULL,
    counted_cash_xof BIGINT NOT NULL,
    discrepancy_xof BIGINT NOT NULL,
    payment_count INTEGER NOT NULL DEFAULT 0,
    reversal_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    notes VARCHAR(500),
    submitted_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    validated_by UUID,
    validated_at TIMESTAMPTZ,
    validation_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cash_closings_tenant_date UNIQUE (tenant_id, closing_date),
    CONSTRAINT chk_cash_closings_amounts CHECK (
        cash_in_xof >= 0
        AND cash_out_reversals_xof >= 0
        AND expected_cash_xof = cash_in_xof - cash_out_reversals_xof
        AND counted_cash_xof >= 0
        AND discrepancy_xof = counted_cash_xof - expected_cash_xof
    ),
    CONSTRAINT chk_cash_closings_counts CHECK (payment_count >= 0 AND reversal_count >= 0),
    CONSTRAINT chk_cash_closings_status CHECK (status IN ('SUBMITTED', 'VALIDATED'))
);

CREATE INDEX idx_cash_closings_tenant_id ON cash_closings (tenant_id);
CREATE INDEX idx_cash_closings_tenant_date ON cash_closings (tenant_id, closing_date DESC);

-- 15. Triggers d'immutabilité sur les 5 tables en ajout seul
CREATE TRIGGER trg_payments_immutable
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION prevent_finance_immutable_mutation();

CREATE TRIGGER trg_payment_allocations_immutable
    BEFORE UPDATE OR DELETE ON payment_allocations
    FOR EACH ROW EXECUTE FUNCTION prevent_finance_immutable_mutation();

CREATE TRIGGER trg_receipts_immutable
    BEFORE UPDATE OR DELETE ON receipts
    FOR EACH ROW EXECUTE FUNCTION prevent_finance_immutable_mutation();

CREATE TRIGGER trg_payment_reversals_immutable
    BEFORE UPDATE OR DELETE ON payment_reversals
    FOR EACH ROW EXECUTE FUNCTION prevent_finance_immutable_mutation();

CREATE TRIGGER trg_ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION prevent_finance_immutable_mutation();

-- 16. Row-Level Security (RLS) activée et forcée (fail-closed) sur les 13 tables métier
ALTER TABLE finance_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE finance_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_finance_sequences ON finance_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE fee_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE fee_items FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_fee_items ON fee_items
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE student_invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_invoices FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_invoices ON student_invoices
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE student_invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_invoice_lines FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_invoice_lines ON student_invoice_lines
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE payment_schedules ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_schedules FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_payment_schedules ON payment_schedules
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE fee_adjustments ENABLE ROW LEVEL SECURITY;
ALTER TABLE fee_adjustments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_fee_adjustments ON fee_adjustments
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_payments ON payments
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE payment_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_allocations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_payment_allocations ON payment_allocations
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE receipts FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_receipts ON receipts
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE payment_reversal_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_reversal_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_payment_reversal_requests ON payment_reversal_requests
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE payment_reversals ENABLE ROW LEVEL SECURITY;
ALTER TABLE payment_reversals FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_payment_reversals ON payment_reversals
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_ledger_entries ON ledger_entries
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE cash_closings ENABLE ROW LEVEL SECURITY;
ALTER TABLE cash_closings FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_cash_closings ON cash_closings
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 17. Droits du rôle applicatif ecole_app (NOBYPASSRLS)
-- Tables strictement en ajout seul : SELECT et INSERT uniquement
GRANT SELECT, INSERT ON
    payments,
    payment_allocations,
    receipts,
    payment_reversals,
    ledger_entries
TO ecole_app;

-- Tables métier à archivage/annulation logique : SELECT, INSERT, UPDATE (aucun DELETE)
GRANT SELECT, INSERT, UPDATE ON
    finance_sequences,
    fee_items,
    student_invoices,
    fee_adjustments,
    payment_reversal_requests,
    cash_closings
TO ecole_app;

-- Lignes et échéances éditables uniquement tant que la facture est en brouillon (DRAFT)
GRANT SELECT, INSERT, UPDATE, DELETE ON
    student_invoice_lines,
    payment_schedules
TO ecole_app;
