-- V9: Module center (mode centre de renforcement / soutien scolaire : forfaits/packs en FCFA,
-- cohortes, affectations intervenants, inscriptions élèves, créneaux récurrents, séances et suivi de participation)

-- 1. Catalogue des forfaits / packs de séances du centre (montants en entiers FCFA)
CREATE TABLE center_session_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID,
    grade_level_id UUID,
    subject_id UUID,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    billing_mode VARCHAR(20) NOT NULL,
    included_sessions INTEGER,
    duration_days INTEGER,
    price_xof BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_session_packs_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_center_session_packs_grade_level
        FOREIGN KEY (tenant_id, grade_level_id) REFERENCES grade_levels(tenant_id, id),
    CONSTRAINT fk_center_session_packs_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_center_session_packs_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_center_session_packs_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_session_packs_billing_mode
        CHECK (billing_mode IN ('SESSION_PACK', 'MONTHLY_FLAT', 'TERM_FLAT')),
    CONSTRAINT chk_center_session_packs_included_sessions CHECK (
        (billing_mode = 'SESSION_PACK' AND included_sessions IS NOT NULL AND included_sessions > 0 AND included_sessions <= 500)
        OR (billing_mode <> 'SESSION_PACK' AND (included_sessions IS NULL OR (included_sessions > 0 AND included_sessions <= 500)))
    ),
    CONSTRAINT chk_center_session_packs_duration_days
        CHECK (duration_days IS NULL OR (duration_days > 0 AND duration_days <= 365)),
    CONSTRAINT chk_center_session_packs_price_xof CHECK (price_xof >= 0)
);

CREATE INDEX idx_center_session_packs_tenant_id ON center_session_packs (tenant_id);
CREATE INDEX idx_center_session_packs_tenant_active ON center_session_packs (tenant_id, active);
CREATE INDEX idx_center_session_packs_tenant_year ON center_session_packs (tenant_id, academic_year_id);

-- 2. Groupes / cohortes de renforcement
CREATE TABLE center_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    grade_level_id UUID,
    subject_id UUID,
    default_pack_id UUID,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    group_type VARCHAR(30) NOT NULL,
    room_name VARCHAR(60),
    capacity INTEGER NOT NULL DEFAULT 20,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    status_reason VARCHAR(255),
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_groups_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_center_groups_grade_level
        FOREIGN KEY (tenant_id, grade_level_id) REFERENCES grade_levels(tenant_id, id),
    CONSTRAINT fk_center_groups_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT fk_center_groups_default_pack
        FOREIGN KEY (tenant_id, default_pack_id) REFERENCES center_session_packs(tenant_id, id),
    CONSTRAINT uq_center_groups_tenant_year_code UNIQUE (tenant_id, academic_year_id, code),
    CONSTRAINT uq_center_groups_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_groups_type CHECK (group_type IN (
        'EXAM_PREP',
        'SUBJECT_SUPPORT',
        'HOLIDAY_CAMP',
        'SMALL_GROUP',
        'INDIVIDUAL'
    )),
    CONSTRAINT chk_center_groups_capacity CHECK (capacity > 0 AND capacity <= 200),
    CONSTRAINT chk_center_groups_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_center_groups_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED'))
);

CREATE INDEX idx_center_groups_tenant_id ON center_groups (tenant_id);
CREATE INDEX idx_center_groups_tenant_year ON center_groups (tenant_id, academic_year_id);
CREATE INDEX idx_center_groups_tenant_status ON center_groups (tenant_id, status);
CREATE INDEX idx_center_groups_tenant_subject ON center_groups (tenant_id, subject_id);

-- 3. Souscriptions / carnets de packs attribués aux élèves
CREATE TABLE center_student_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    student_id UUID NOT NULL,
    pack_id UUID NOT NULL,
    group_id UUID,
    pack_code_snapshot VARCHAR(30) NOT NULL,
    pack_name_snapshot VARCHAR(120) NOT NULL,
    billing_mode VARCHAR(20) NOT NULL,
    price_xof BIGINT NOT NULL,
    total_sessions INTEGER,
    consumed_sessions INTEGER NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(255),
    assigned_by UUID NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_student_packs_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_center_student_packs_pack
        FOREIGN KEY (tenant_id, pack_id) REFERENCES center_session_packs(tenant_id, id),
    CONSTRAINT fk_center_student_packs_group
        FOREIGN KEY (tenant_id, group_id) REFERENCES center_groups(tenant_id, id),
    CONSTRAINT uq_center_student_packs_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_student_packs_billing_mode
        CHECK (billing_mode IN ('SESSION_PACK', 'MONTHLY_FLAT', 'TERM_FLAT')),
    CONSTRAINT chk_center_student_packs_price_xof CHECK (price_xof >= 0),
    CONSTRAINT chk_center_student_packs_total_sessions CHECK (
        (billing_mode = 'SESSION_PACK' AND total_sessions IS NOT NULL AND total_sessions > 0 AND total_sessions <= 500)
        OR (billing_mode <> 'SESSION_PACK' AND (total_sessions IS NULL OR (total_sessions > 0 AND total_sessions <= 500)))
    ),
    CONSTRAINT chk_center_student_packs_consumed CHECK (
        consumed_sessions >= 0 AND (total_sessions IS NULL OR consumed_sessions <= total_sessions)
    ),
    CONSTRAINT chk_center_student_packs_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_center_student_packs_status CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_center_student_packs_tenant_id ON center_student_packs (tenant_id);
CREATE INDEX idx_center_student_packs_tenant_student ON center_student_packs (tenant_id, student_id, status);
CREATE INDEX idx_center_student_packs_tenant_group ON center_student_packs (tenant_id, group_id, status);

-- 4. Affectations des intervenants / enseignants aux cohortes
CREATE TABLE center_group_tutors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    group_id UUID NOT NULL,
    teacher_id UUID NOT NULL,
    subject_id UUID,
    lead_tutor BOOLEAN NOT NULL DEFAULT FALSE,
    hourly_rate_xof BIGINT,
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_group_tutors_group
        FOREIGN KEY (tenant_id, group_id) REFERENCES center_groups(tenant_id, id),
    CONSTRAINT fk_center_group_tutors_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT fk_center_group_tutors_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_center_group_tutors_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_group_tutors_hourly_rate CHECK (hourly_rate_xof IS NULL OR hourly_rate_xof >= 0),
    CONSTRAINT chk_center_group_tutors_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uq_center_group_tutors_active_assignment
    ON center_group_tutors (
        tenant_id,
        group_id,
        teacher_id,
        COALESCE(subject_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE active = TRUE;

CREATE UNIQUE INDEX uq_center_group_tutors_active_lead_per_group
    ON center_group_tutors (tenant_id, group_id)
    WHERE active = TRUE AND lead_tutor = TRUE;

CREATE INDEX idx_center_group_tutors_tenant_id ON center_group_tutors (tenant_id);
CREATE INDEX idx_center_group_tutors_tenant_group ON center_group_tutors (tenant_id, group_id, active);
CREATE INDEX idx_center_group_tutors_tenant_teacher ON center_group_tutors (tenant_id, teacher_id, active);

-- 5. Inscriptions des élèves aux cohortes
CREATE TABLE center_group_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    group_id UUID NOT NULL,
    student_id UUID NOT NULL,
    student_pack_id UUID,
    enrolled_at DATE NOT NULL,
    ended_at DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(255),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_group_enrollments_group
        FOREIGN KEY (tenant_id, group_id) REFERENCES center_groups(tenant_id, id),
    CONSTRAINT fk_center_group_enrollments_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_center_group_enrollments_student_pack
        FOREIGN KEY (tenant_id, student_pack_id) REFERENCES center_student_packs(tenant_id, id),
    CONSTRAINT uq_center_group_enrollments_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_group_enrollments_dates CHECK (ended_at IS NULL OR ended_at >= enrolled_at),
    CONSTRAINT chk_center_group_enrollments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_center_group_enrollments_active_student
    ON center_group_enrollments (tenant_id, group_id, student_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_center_group_enrollments_tenant_id ON center_group_enrollments (tenant_id);
CREATE INDEX idx_center_group_enrollments_tenant_group ON center_group_enrollments (tenant_id, group_id, status);
CREATE INDEX idx_center_group_enrollments_tenant_student ON center_group_enrollments (tenant_id, student_id, status);

-- 6. Créneaux récurrents hebdomadaires (planning des cohortes)
CREATE TABLE center_schedule_slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    group_id UUID NOT NULL,
    teacher_id UUID,
    subject_id UUID,
    day_of_week VARCHAR(15) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room_name VARCHAR(60),
    effective_from DATE,
    effective_until DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_schedule_slots_group
        FOREIGN KEY (tenant_id, group_id) REFERENCES center_groups(tenant_id, id),
    CONSTRAINT fk_center_schedule_slots_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT fk_center_schedule_slots_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_center_schedule_slots_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_schedule_slots_day CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
    )),
    CONSTRAINT chk_center_schedule_slots_times CHECK (end_time > start_time),
    CONSTRAINT chk_center_schedule_slots_dates CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until >= effective_from
    )
);

CREATE INDEX idx_center_schedule_slots_tenant_id ON center_schedule_slots (tenant_id);
CREATE INDEX idx_center_schedule_slots_tenant_group ON center_schedule_slots (tenant_id, group_id, active);
CREATE INDEX idx_center_schedule_slots_tenant_teacher ON center_schedule_slots (tenant_id, teacher_id, active);
CREATE INDEX idx_center_schedule_slots_tenant_day ON center_schedule_slots (tenant_id, day_of_week, active);

-- 7. Séances concrètes planifiées / réalisées
CREATE TABLE center_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    group_id UUID NOT NULL,
    schedule_slot_id UUID,
    teacher_id UUID,
    subject_id UUID,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    duration_minutes INTEGER NOT NULL,
    room_name VARCHAR(60),
    topic VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    recorded_by UUID NOT NULL,
    completed_by UUID,
    completed_at TIMESTAMPTZ,
    cancelled_reason VARCHAR(255),
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_sessions_group
        FOREIGN KEY (tenant_id, group_id) REFERENCES center_groups(tenant_id, id),
    CONSTRAINT fk_center_sessions_schedule_slot
        FOREIGN KEY (tenant_id, schedule_slot_id) REFERENCES center_schedule_slots(tenant_id, id),
    CONSTRAINT fk_center_sessions_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT fk_center_sessions_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_center_sessions_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_center_sessions_times CHECK (end_time > start_time),
    CONSTRAINT chk_center_sessions_duration CHECK (duration_minutes > 0 AND duration_minutes <= 720),
    CONSTRAINT chk_center_sessions_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_center_sessions_tenant_id ON center_sessions (tenant_id);
CREATE INDEX idx_center_sessions_tenant_group_date ON center_sessions (tenant_id, group_id, session_date DESC);
CREATE INDEX idx_center_sessions_tenant_teacher_date ON center_sessions (tenant_id, teacher_id, session_date DESC);
CREATE INDEX idx_center_sessions_tenant_date_status ON center_sessions (tenant_id, session_date DESC, status);

-- 8. Suivi individuel de présence, participation et consommation de pack par séance
CREATE TABLE center_session_attendances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    enrollment_id UUID NOT NULL,
    consumed_student_pack_id UUID,
    status VARCHAR(20) NOT NULL,
    late_minutes INTEGER,
    participation_rating INTEGER,
    homework_done BOOLEAN,
    remarks VARCHAR(500),
    recorded_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_center_session_attendances_session
        FOREIGN KEY (tenant_id, session_id) REFERENCES center_sessions(tenant_id, id),
    CONSTRAINT fk_center_session_attendances_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_center_session_attendances_enrollment
        FOREIGN KEY (tenant_id, enrollment_id) REFERENCES center_group_enrollments(tenant_id, id),
    CONSTRAINT fk_center_session_attendances_consumed_pack
        FOREIGN KEY (tenant_id, consumed_student_pack_id) REFERENCES center_student_packs(tenant_id, id),
    CONSTRAINT uq_center_session_attendances_tenant_session_student UNIQUE (tenant_id, session_id, student_id),
    CONSTRAINT chk_center_session_attendances_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    CONSTRAINT chk_center_session_attendances_late_minutes CHECK (
        (status = 'LATE' AND late_minutes IS NOT NULL AND late_minutes > 0 AND late_minutes <= 300)
        OR (status <> 'LATE' AND late_minutes IS NULL)
    ),
    CONSTRAINT chk_center_session_attendances_participation CHECK (
        participation_rating IS NULL OR (participation_rating >= 1 AND participation_rating <= 5)
    )
);

CREATE INDEX idx_center_session_attendances_tenant_id ON center_session_attendances (tenant_id);
CREATE INDEX idx_center_session_attendances_tenant_session ON center_session_attendances (tenant_id, session_id);
CREATE INDEX idx_center_session_attendances_tenant_student ON center_session_attendances (tenant_id, student_id);
CREATE INDEX idx_center_session_attendances_tenant_pack ON center_session_attendances (tenant_id, consumed_student_pack_id);

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 8 TABLES
-- ============================================================================

ALTER TABLE center_session_packs ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_session_packs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_session_packs ON center_session_packs
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_groups FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_groups ON center_groups
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_student_packs ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_student_packs FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_student_packs ON center_student_packs
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_group_tutors ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_group_tutors FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_group_tutors ON center_group_tutors
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_group_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_group_enrollments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_group_enrollments ON center_group_enrollments
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_schedule_slots ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_schedule_slots FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_schedule_slots ON center_schedule_slots
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_sessions ON center_sessions
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE center_session_attendances ENABLE ROW LEVEL SECURITY;
ALTER TABLE center_session_attendances FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_center_session_attendances ON center_session_attendances
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS, archivage logique uniquement)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    center_session_packs,
    center_groups,
    center_student_packs,
    center_group_tutors,
    center_group_enrollments,
    center_schedule_slots,
    center_sessions,
    center_session_attendances
TO ecole_app;
