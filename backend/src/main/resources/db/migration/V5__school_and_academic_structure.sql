-- V5: Enrichissement des établissements et structure académique complète (niveaux, classes, matières, périodes d'évaluation)

-- 1. Enrichissement de la table schools (informations d'établissement et paramètres pédagogiques par défaut)
ALTER TABLE schools
    ADD COLUMN short_name VARCHAR(60),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN region VARCHAR(100),
    ADD COLUMN country_code VARCHAR(2) NOT NULL DEFAULT 'SN',
    ADD COLUMN motto VARCHAR(200),
    ADD COLUMN ministry_code VARCHAR(60),
    ADD COLUMN website VARCHAR(255),
    ADD COLUMN logo_object_key VARCHAR(512),
    ADD COLUMN grading_period_system VARCHAR(20) NOT NULL DEFAULT 'TRIMESTER',
    ADD COLUMN grading_scale INTEGER NOT NULL DEFAULT 20,
    ADD COLUMN status_reason VARCHAR(255),
    ADD COLUMN suspended_at TIMESTAMPTZ,
    ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE schools
    ADD CONSTRAINT chk_schools_country_code
        CHECK (country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT chk_schools_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    ADD CONSTRAINT chk_schools_grading_period_system
        CHECK (grading_period_system IN ('TRIMESTER', 'SEMESTER', 'CUSTOM')),
    ADD CONSTRAINT chk_schools_grading_scale
        CHECK (grading_scale IN (10, 20, 100));

CREATE INDEX idx_schools_status ON schools (status);
CREATE INDEX idx_schools_type ON schools (school_type);

-- 2. Enrichissement de la table academic_years (système de périodes, horodatages de clôture/archivage, unicité composite pour FKs)
ALTER TABLE academic_years
    ADD COLUMN period_system VARCHAR(20) NOT NULL DEFAULT 'TRIMESTER',
    ADD COLUMN closed_at TIMESTAMPTZ,
    ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE academic_years
    ADD CONSTRAINT chk_academic_years_period_system
        CHECK (period_system IN ('TRIMESTER', 'SEMESTER', 'CUSTOM')),
    ADD CONSTRAINT uq_academic_years_tenant_id_id
        UNIQUE (tenant_id, id);

CREATE INDEX idx_academic_years_tenant_dates
    ON academic_years (tenant_id, start_date DESC);

-- 3. Périodes d'évaluation (trimestres, semestres ou périodes personnalisées par année scolaire)
CREATE TABLE evaluation_periods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    sequence_order INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_evaluation_periods_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT uq_evaluation_periods_tenant_year_code
        UNIQUE (tenant_id, academic_year_id, code),
    CONSTRAINT uq_evaluation_periods_tenant_year_order
        UNIQUE (tenant_id, academic_year_id, sequence_order),
    CONSTRAINT chk_evaluation_periods_dates
        CHECK (end_date > start_date),
    CONSTRAINT chk_evaluation_periods_order
        CHECK (sequence_order > 0),
    CONSTRAINT chk_evaluation_periods_weight
        CHECK (weight > 0 AND weight <= 100),
    CONSTRAINT chk_evaluation_periods_type
        CHECK (period_type IN ('TRIMESTER', 'SEMESTER', 'CUSTOM')),
    CONSTRAINT chk_evaluation_periods_status
        CHECK (status IN ('PLANNED', 'OPEN', 'CLOSED', 'LOCKED'))
);

CREATE INDEX idx_evaluation_periods_tenant_id
    ON evaluation_periods (tenant_id);
CREATE INDEX idx_evaluation_periods_tenant_year_order
    ON evaluation_periods (tenant_id, academic_year_id, sequence_order);

-- 4. Niveaux scolaires et niveaux de centre de renforcement
CREATE TABLE grade_levels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    cycle VARCHAR(30) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_grade_levels_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_grade_levels_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_grade_levels_cycle
        CHECK (cycle IN ('PRESCHOOL', 'PRIMARY', 'MIDDLE_SCHOOL', 'HIGH_SCHOOL', 'TUTORING', 'OTHER')),
    CONSTRAINT chk_grade_levels_order CHECK (display_order > 0)
);

CREATE INDEX idx_grade_levels_tenant_id
    ON grade_levels (tenant_id);
CREATE INDEX idx_grade_levels_tenant_order
    ON grade_levels (tenant_id, display_order);

-- 5. Référentiel des matières par établissement
CREATE TABLE subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    short_name VARCHAR(30),
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    default_coefficient INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subjects_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_subjects_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_subjects_category
        CHECK (category IN ('SCIENTIFIC', 'LITERARY', 'LANGUAGE', 'HUMANITIES', 'ARTS_SPORTS', 'TECHNICAL', 'GENERAL')),
    CONSTRAINT chk_subjects_default_coef
        CHECK (default_coefficient > 0 AND default_coefficient <= 50)
);

CREATE INDEX idx_subjects_tenant_id
    ON subjects (tenant_id);
CREATE INDEX idx_subjects_tenant_name
    ON subjects (tenant_id, name);

-- 6. Classes et groupes par année scolaire et niveau
CREATE TABLE class_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    grade_level_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    series VARCHAR(30),
    room_name VARCHAR(60),
    capacity INTEGER NOT NULL DEFAULT 40,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_rooms_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_class_rooms_grade_level
        FOREIGN KEY (tenant_id, grade_level_id) REFERENCES grade_levels(tenant_id, id),
    CONSTRAINT uq_class_rooms_tenant_year_code
        UNIQUE (tenant_id, academic_year_id, code),
    CONSTRAINT uq_class_rooms_tenant_id_id
        UNIQUE (tenant_id, id),
    CONSTRAINT chk_class_rooms_capacity
        CHECK (capacity > 0 AND capacity <= 500),
    CONSTRAINT chk_class_rooms_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_class_rooms_tenant_id
    ON class_rooms (tenant_id);
CREATE INDEX idx_class_rooms_tenant_year
    ON class_rooms (tenant_id, academic_year_id);
CREATE INDEX idx_class_rooms_tenant_level
    ON class_rooms (tenant_id, grade_level_id);

-- 7. Association matières <-> classes avec coefficient effectif et volume horaire
CREATE TABLE class_subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    class_room_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    coefficient INTEGER NOT NULL,
    weekly_hours INTEGER,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_class_subjects_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_class_subjects_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_class_subjects_tenant_class_subject
        UNIQUE (tenant_id, class_room_id, subject_id),
    CONSTRAINT chk_class_subjects_coef
        CHECK (coefficient > 0 AND coefficient <= 50),
    CONSTRAINT chk_class_subjects_weekly_hours
        CHECK (weekly_hours IS NULL OR (weekly_hours > 0 AND weekly_hours <= 40))
);

CREATE INDEX idx_class_subjects_tenant_id
    ON class_subjects (tenant_id);
CREATE INDEX idx_class_subjects_tenant_class
    ON class_subjects (tenant_id, class_room_id);
CREATE INDEX idx_class_subjects_tenant_subject
    ON class_subjects (tenant_id, subject_id);

-- 8. Row-Level Security (RLS) activée et forcée sur toutes les nouvelles tables métier (fail-closed)
ALTER TABLE evaluation_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluation_periods FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_evaluation_periods ON evaluation_periods
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE grade_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE grade_levels FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_grade_levels ON grade_levels
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE subjects FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_subjects ON subjects
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE class_rooms ENABLE ROW LEVEL SECURITY;
ALTER TABLE class_rooms FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_class_rooms ON class_rooms
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE class_subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE class_subjects FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_class_subjects ON class_subjects
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 9. Droits du rôle applicatif non-superuser (soumis strictement à la RLS, NOBYPASSRLS)
GRANT SELECT, INSERT, UPDATE, DELETE ON
    evaluation_periods,
    grade_levels,
    subjects,
    class_rooms,
    class_subjects
TO ecole_app;
