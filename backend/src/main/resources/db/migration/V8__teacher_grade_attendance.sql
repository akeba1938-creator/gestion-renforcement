-- V8: Modules teacher, grade et attendance (dossiers enseignants, affectations, évaluations, notes, bulletins de période et feuilles d'appel)

-- 1. Contraintes d'unicité composites manquantes sur des tables existantes pour les clés étrangères intra-tenant
ALTER TABLE users
    ADD CONSTRAINT uq_users_tenant_id_id UNIQUE (tenant_id, id);

ALTER TABLE evaluation_periods
    ADD CONSTRAINT uq_evaluation_periods_tenant_id_id UNIQUE (tenant_id, id);

ALTER TABLE class_subjects
    ADD CONSTRAINT uq_class_subjects_tenant_id_id UNIQUE (tenant_id, id);

-- ============================================================================
-- MODULE TEACHER : dossiers enseignants/intervenants, spécialités, affectations
-- ============================================================================

-- 2. Dossiers enseignants et intervenants (écoles privées et centres de renforcement)
CREATE TABLE teachers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    employee_code VARCHAR(32) NOT NULL,
    user_id UUID,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    teacher_type VARCHAR(20) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    secondary_phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    national_id_number VARCHAR(60),
    hire_date DATE,
    hourly_rate_xof BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(255),
    archived_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_teachers_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users(tenant_id, id),
    CONSTRAINT uq_teachers_tenant_employee_code UNIQUE (tenant_id, employee_code),
    CONSTRAINT uq_teachers_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_teachers_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_teachers_type CHECK (teacher_type IN ('PERMANENT', 'PART_TIME', 'TUTOR')),
    CONSTRAINT chk_teachers_hourly_rate CHECK (hourly_rate_xof IS NULL OR hourly_rate_xof >= 0),
    CONSTRAINT chk_teachers_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_teachers_tenant_user_id
    ON teachers (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_teachers_tenant_id ON teachers (tenant_id);
CREATE INDEX idx_teachers_tenant_status ON teachers (tenant_id, status);
CREATE INDEX idx_teachers_tenant_name ON teachers (tenant_id, last_name, first_name);

-- 3. Spécialités (matières de compétence) des enseignants
CREATE TABLE teacher_specialties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    teacher_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    primary_specialty BOOLEAN NOT NULL DEFAULT FALSE,
    notes VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_teacher_specialties_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT fk_teacher_specialties_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT uq_teacher_specialties_tenant_teacher_subject UNIQUE (tenant_id, teacher_id, subject_id)
);

CREATE UNIQUE INDEX uq_teacher_specialties_primary_per_teacher
    ON teacher_specialties (tenant_id, teacher_id)
    WHERE primary_specialty = TRUE;

CREATE INDEX idx_teacher_specialties_tenant_id ON teacher_specialties (tenant_id);
CREATE INDEX idx_teacher_specialties_tenant_teacher ON teacher_specialties (tenant_id, teacher_id);
CREATE INDEX idx_teacher_specialties_tenant_subject ON teacher_specialties (tenant_id, subject_id);

-- 4. Affectations des enseignants aux classes et matières par année scolaire
CREATE TABLE teacher_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    teacher_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    class_room_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    class_subject_id UUID NOT NULL,
    homeroom_teacher BOOLEAN NOT NULL DEFAULT FALSE,
    weekly_hours INTEGER,
    start_date DATE,
    end_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_teacher_assignments_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT fk_teacher_assignments_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_teacher_assignments_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_teacher_assignments_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT fk_teacher_assignments_class_subject
        FOREIGN KEY (tenant_id, class_subject_id) REFERENCES class_subjects(tenant_id, id),
    CONSTRAINT chk_teacher_assignments_weekly_hours
        CHECK (weekly_hours IS NULL OR (weekly_hours > 0 AND weekly_hours <= 40)),
    CONSTRAINT chk_teacher_assignments_dates
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE UNIQUE INDEX uq_teacher_assignments_active_teacher_class_subject
    ON teacher_assignments (tenant_id, teacher_id, class_room_id, subject_id)
    WHERE active = TRUE;

CREATE UNIQUE INDEX uq_teacher_assignments_active_homeroom_per_class
    ON teacher_assignments (tenant_id, class_room_id)
    WHERE active = TRUE AND homeroom_teacher = TRUE;

CREATE INDEX idx_teacher_assignments_tenant_id ON teacher_assignments (tenant_id);
CREATE INDEX idx_teacher_assignments_tenant_teacher ON teacher_assignments (tenant_id, teacher_id, active);
CREATE INDEX idx_teacher_assignments_tenant_class ON teacher_assignments (tenant_id, class_room_id, active);
CREATE INDEX idx_teacher_assignments_tenant_year ON teacher_assignments (tenant_id, academic_year_id, active);

-- ============================================================================
-- MODULE GRADE : évaluations, notes, bulletins de période et résultats matière
-- ============================================================================

-- 5. Évaluations (devoirs, interrogations, compositions, examens)
CREATE TABLE evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    evaluation_period_id UUID NOT NULL,
    class_room_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    class_subject_id UUID NOT NULL,
    teacher_id UUID,
    title VARCHAR(150) NOT NULL,
    evaluation_type VARCHAR(25) NOT NULL,
    evaluation_date DATE NOT NULL,
    max_score NUMERIC(6, 2) NOT NULL,
    weight INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    validated_by UUID,
    validated_at TIMESTAMPTZ,
    locked_by UUID,
    locked_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_evaluations_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_evaluations_period
        FOREIGN KEY (tenant_id, evaluation_period_id) REFERENCES evaluation_periods(tenant_id, id),
    CONSTRAINT fk_evaluations_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_evaluations_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT fk_evaluations_class_subject
        FOREIGN KEY (tenant_id, class_subject_id) REFERENCES class_subjects(tenant_id, id),
    CONSTRAINT fk_evaluations_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT uq_evaluations_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_evaluations_type CHECK (evaluation_type IN (
        'HOMEWORK',
        'QUIZ',
        'TEST',
        'COMPOSITION',
        'EXAM',
        'ORAL',
        'PRACTICAL'
    )),
    CONSTRAINT chk_evaluations_max_score CHECK (max_score > 0 AND max_score <= 100),
    CONSTRAINT chk_evaluations_weight CHECK (weight > 0 AND weight <= 20),
    CONSTRAINT chk_evaluations_status CHECK (status IN ('DRAFT', 'VALIDATED', 'LOCKED', 'CANCELLED'))
);

CREATE INDEX idx_evaluations_tenant_id ON evaluations (tenant_id);
CREATE INDEX idx_evaluations_tenant_period_class ON evaluations (tenant_id, evaluation_period_id, class_room_id);
CREATE INDEX idx_evaluations_tenant_class_subject ON evaluations (tenant_id, class_room_id, subject_id);
CREATE INDEX idx_evaluations_tenant_date ON evaluations (tenant_id, evaluation_date DESC);

-- 6. Notes individuelles des élèves sur une évaluation
CREATE TABLE student_grades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    evaluation_id UUID NOT NULL,
    student_id UUID NOT NULL,
    attendance_status VARCHAR(25) NOT NULL DEFAULT 'PRESENT',
    score NUMERIC(6, 2),
    comment VARCHAR(500),
    entered_by UUID NOT NULL,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_student_grades_evaluation
        FOREIGN KEY (tenant_id, evaluation_id) REFERENCES evaluations(tenant_id, id),
    CONSTRAINT fk_student_grades_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_student_grades_tenant_eval_student UNIQUE (tenant_id, evaluation_id, student_id),
    CONSTRAINT chk_student_grades_attendance CHECK (attendance_status IN (
        'PRESENT',
        'ABSENT_JUSTIFIED',
        'ABSENT_UNJUSTIFIED',
        'EXEMPTED'
    )),
    CONSTRAINT chk_student_grades_score CHECK (
        (attendance_status = 'PRESENT' AND score IS NOT NULL AND score >= 0 AND score <= 100)
        OR (attendance_status <> 'PRESENT' AND score IS NULL)
    )
);

CREATE INDEX idx_student_grades_tenant_id ON student_grades (tenant_id);
CREATE INDEX idx_student_grades_tenant_eval ON student_grades (tenant_id, evaluation_id);
CREATE INDEX idx_student_grades_tenant_student ON student_grades (tenant_id, student_id);

-- 7. Bulletins / synthèses de période par élève
CREATE TABLE period_report_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    evaluation_period_id UUID NOT NULL,
    class_room_id UUID NOT NULL,
    student_id UUID NOT NULL,
    grading_scale INTEGER NOT NULL,
    general_average NUMERIC(6, 2),
    class_average NUMERIC(6, 2),
    min_class_average NUMERIC(6, 2),
    max_class_average NUMERIC(6, 2),
    rank_in_class INTEGER,
    class_size INTEGER NOT NULL DEFAULT 0,
    tied_rank BOOLEAN NOT NULL DEFAULT FALSE,
    total_weighted_score NUMERIC(10, 2),
    total_coefficients INTEGER NOT NULL DEFAULT 0,
    mention VARCHAR(30) NOT NULL DEFAULT 'NONE',
    council_decision VARCHAR(30) NOT NULL DEFAULT 'NONE',
    homeroom_appreciation VARCHAR(1000),
    director_appreciation VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    validated_by UUID,
    validated_at TIMESTAMPTZ,
    locked_by UUID,
    locked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_period_report_cards_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_period_report_cards_period
        FOREIGN KEY (tenant_id, evaluation_period_id) REFERENCES evaluation_periods(tenant_id, id),
    CONSTRAINT fk_period_report_cards_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_period_report_cards_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_period_report_cards_tenant_period_student UNIQUE (tenant_id, evaluation_period_id, student_id),
    CONSTRAINT uq_period_report_cards_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_period_report_cards_scale CHECK (grading_scale IN (10, 20, 100)),
    CONSTRAINT chk_period_report_cards_rank CHECK (rank_in_class IS NULL OR rank_in_class > 0),
    CONSTRAINT chk_period_report_cards_class_size CHECK (class_size >= 0),
    CONSTRAINT chk_period_report_cards_total_coef CHECK (total_coefficients >= 0),
    CONSTRAINT chk_period_report_cards_mention CHECK (mention IN (
        'EXCELLENT',
        'TRES_BIEN',
        'BIEN',
        'ASSEZ_BIEN',
        'PASSABLE',
        'INSUFFISANT',
        'MEDIOCRE',
        'NONE'
    )),
    CONSTRAINT chk_period_report_cards_decision CHECK (council_decision IN (
        'FELICITATIONS',
        'ENCOURAGEMENTS',
        'TABLEAU_HONNEUR',
        'AVERTISSEMENT_TRAVAIL',
        'BLAME_TRAVAIL',
        'PASSAGE',
        'REDOUBLEMENT',
        'NONE'
    )),
    CONSTRAINT chk_period_report_cards_status CHECK (status IN ('DRAFT', 'VALIDATED', 'LOCKED'))
);

CREATE INDEX idx_period_report_cards_tenant_id ON period_report_cards (tenant_id);
CREATE INDEX idx_period_report_cards_tenant_period_class ON period_report_cards (tenant_id, evaluation_period_id, class_room_id);
CREATE INDEX idx_period_report_cards_tenant_student_year ON period_report_cards (tenant_id, student_id, academic_year_id);

-- 8. Résultats et appréciations par matière sur un bulletin de période
CREATE TABLE period_subject_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    report_card_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    class_subject_id UUID NOT NULL,
    subject_code VARCHAR(20) NOT NULL,
    subject_name VARCHAR(120) NOT NULL,
    coefficient INTEGER NOT NULL,
    average_score NUMERIC(6, 2),
    class_subject_average NUMERIC(6, 2),
    rank_in_subject INTEGER,
    evaluation_count INTEGER NOT NULL DEFAULT 0,
    teacher_appreciation VARCHAR(500),
    appreciated_by UUID,
    appreciated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_period_subject_results_report_card
        FOREIGN KEY (tenant_id, report_card_id) REFERENCES period_report_cards(tenant_id, id),
    CONSTRAINT fk_period_subject_results_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT fk_period_subject_results_class_subject
        FOREIGN KEY (tenant_id, class_subject_id) REFERENCES class_subjects(tenant_id, id),
    CONSTRAINT uq_period_subject_results_tenant_card_subject UNIQUE (tenant_id, report_card_id, subject_id),
    CONSTRAINT chk_period_subject_results_coef CHECK (coefficient > 0 AND coefficient <= 50),
    CONSTRAINT chk_period_subject_results_rank CHECK (rank_in_subject IS NULL OR rank_in_subject > 0),
    CONSTRAINT chk_period_subject_results_eval_count CHECK (evaluation_count >= 0)
);

CREATE INDEX idx_period_subject_results_tenant_id ON period_subject_results (tenant_id);
CREATE INDEX idx_period_subject_results_tenant_card ON period_subject_results (tenant_id, report_card_id);
CREATE INDEX idx_period_subject_results_tenant_subject ON period_subject_results (tenant_id, subject_id);

-- ============================================================================
-- MODULE ATTENDANCE : séances d'appel et pointages élèves
-- ============================================================================

-- 9. Séances / feuilles d'appel par classe (et optionnellement matière, enseignant ou créneau)
CREATE TABLE attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    academic_year_id UUID NOT NULL,
    evaluation_period_id UUID,
    class_room_id UUID NOT NULL,
    subject_id UUID,
    teacher_id UUID,
    session_date DATE NOT NULL,
    time_slot VARCHAR(20) NOT NULL DEFAULT 'MORNING',
    start_time TIME,
    end_time TIME,
    label VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    recorded_by UUID NOT NULL,
    submitted_at TIMESTAMPTZ,
    locked_by UUID,
    locked_at TIMESTAMPTZ,
    notes VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_attendance_sessions_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_attendance_sessions_period
        FOREIGN KEY (tenant_id, evaluation_period_id) REFERENCES evaluation_periods(tenant_id, id),
    CONSTRAINT fk_attendance_sessions_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT fk_attendance_sessions_subject
        FOREIGN KEY (tenant_id, subject_id) REFERENCES subjects(tenant_id, id),
    CONSTRAINT fk_attendance_sessions_teacher
        FOREIGN KEY (tenant_id, teacher_id) REFERENCES teachers(tenant_id, id),
    CONSTRAINT uq_attendance_sessions_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_attendance_sessions_slot CHECK (time_slot IN ('MORNING', 'AFTERNOON', 'FULL_DAY', 'CUSTOM')),
    CONSTRAINT chk_attendance_sessions_times CHECK (
        start_time IS NULL OR end_time IS NULL OR end_time > start_time
    ),
    CONSTRAINT chk_attendance_sessions_status CHECK (status IN ('OPEN', 'SUBMITTED', 'LOCKED', 'CANCELLED'))
);

CREATE INDEX idx_attendance_sessions_tenant_id ON attendance_sessions (tenant_id);
CREATE INDEX idx_attendance_sessions_tenant_class_date ON attendance_sessions (tenant_id, class_room_id, session_date DESC);
CREATE INDEX idx_attendance_sessions_tenant_period ON attendance_sessions (tenant_id, evaluation_period_id);
CREATE INDEX idx_attendance_sessions_tenant_year_date ON attendance_sessions (tenant_id, academic_year_id, session_date DESC);

-- 10. Enregistrements individuels de présence, retard, absence et justification
CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    session_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    late_minutes INTEGER,
    justified BOOLEAN NOT NULL DEFAULT FALSE,
    justification_reason VARCHAR(500),
    justification_document_ref VARCHAR(512),
    justified_by UUID,
    justified_at TIMESTAMPTZ,
    notes VARCHAR(500),
    recorded_by UUID NOT NULL,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_attendance_records_session
        FOREIGN KEY (tenant_id, session_id) REFERENCES attendance_sessions(tenant_id, id),
    CONSTRAINT fk_attendance_records_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT uq_attendance_records_tenant_session_student UNIQUE (tenant_id, session_id, student_id),
    CONSTRAINT chk_attendance_records_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    CONSTRAINT chk_attendance_records_late_minutes CHECK (
        (status = 'LATE' AND late_minutes IS NOT NULL AND late_minutes > 0 AND late_minutes <= 300)
        OR (status <> 'LATE' AND late_minutes IS NULL)
    ),
    CONSTRAINT chk_attendance_records_excused_justified CHECK (
        status <> 'EXCUSED' OR justified = TRUE
    )
);

CREATE INDEX idx_attendance_records_tenant_id ON attendance_records (tenant_id);
CREATE INDEX idx_attendance_records_tenant_session ON attendance_records (tenant_id, session_id);
CREATE INDEX idx_attendance_records_tenant_student ON attendance_records (tenant_id, student_id);

-- ============================================================================
-- ROW-LEVEL SECURITY (RLS) ACTIVÉE ET FORCÉE (FAIL-CLOSED) SUR LES 9 TABLES
-- ============================================================================

ALTER TABLE teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE teachers FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_teachers ON teachers
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE teacher_specialties ENABLE ROW LEVEL SECURITY;
ALTER TABLE teacher_specialties FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_teacher_specialties ON teacher_specialties
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE teacher_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE teacher_assignments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_teacher_assignments ON teacher_assignments
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE evaluations ENABLE ROW LEVEL SECURITY;
ALTER TABLE evaluations FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_evaluations ON evaluations
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE student_grades ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_grades FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_grades ON student_grades
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE period_report_cards ENABLE ROW LEVEL SECURITY;
ALTER TABLE period_report_cards FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_period_report_cards ON period_report_cards
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE period_subject_results ENABLE ROW LEVEL SECURITY;
ALTER TABLE period_subject_results FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_period_subject_results ON period_subject_results
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_attendance_sessions ON attendance_sessions
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_records FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_attendance_records ON attendance_records
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- ============================================================================
-- DROITS DU RÔLE APPLICATIF ecole_app (NOBYPASSRLS)
-- ============================================================================

GRANT SELECT, INSERT, UPDATE ON
    teachers,
    teacher_assignments,
    evaluations,
    student_grades,
    period_report_cards,
    attendance_sessions,
    attendance_records
TO ecole_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    teacher_specialties,
    period_subject_results
TO ecole_app;
