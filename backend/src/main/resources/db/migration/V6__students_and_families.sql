-- V6: Modules student et family (dossiers élèves, séquences de matricules, inscriptions annuelles, familles, tuteurs et liens)

-- 1. Séquence de génération des matricules isolée par établissement et par année
CREATE TABLE student_matricule_sequences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    sequence_year INTEGER NOT NULL,
    last_value INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_student_matricule_sequences_tenant_year UNIQUE (tenant_id, sequence_year),
    CONSTRAINT chk_student_matricule_sequences_year CHECK (sequence_year >= 2000 AND sequence_year <= 2100),
    CONSTRAINT chk_student_matricule_sequences_value CHECK (last_value >= 0)
);

CREATE INDEX idx_student_matricule_sequences_tenant_id
    ON student_matricule_sequences (tenant_id);

-- 2. Dossiers élèves par établissement (archivage logique, matricule unique par tenant)
CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    matricule VARCHAR(64) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    birth_date DATE NOT NULL,
    birth_place VARCHAR(120),
    nationality VARCHAR(80),
    address TEXT,
    phone VARCHAR(20),
    email VARCHAR(255),
    photo_object_key VARCHAR(512),
    medical_notes TEXT,
    admission_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(255),
    archived_at TIMESTAMPTZ,
    user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_students_tenant_matricule UNIQUE (tenant_id, matricule),
    CONSTRAINT uq_students_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_students_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_students_status CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'GRADUATED', 'WITHDRAWN', 'ARCHIVED')),
    CONSTRAINT chk_students_birth_date CHECK (birth_date >= DATE '1900-01-01')
);

CREATE UNIQUE INDEX uq_students_tenant_user_id
    ON students (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_students_tenant_id
    ON students (tenant_id);
CREATE INDEX idx_students_tenant_status
    ON students (tenant_id, status);
CREATE INDEX idx_students_tenant_name
    ON students (tenant_id, last_name, first_name);

-- 3. Inscriptions annuelles et affectations aux classes par établissement
CREATE TABLE student_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    student_id UUID NOT NULL,
    academic_year_id UUID NOT NULL,
    class_room_id UUID NOT NULL,
    enrollment_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    enrollment_date DATE NOT NULL,
    end_date DATE,
    previous_school VARCHAR(150),
    repeating BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    status_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_student_enrollments_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_student_enrollments_academic_year
        FOREIGN KEY (tenant_id, academic_year_id) REFERENCES academic_years(tenant_id, id),
    CONSTRAINT fk_student_enrollments_class_room
        FOREIGN KEY (tenant_id, class_room_id) REFERENCES class_rooms(tenant_id, id),
    CONSTRAINT chk_student_enrollments_type
        CHECK (enrollment_type IN ('NEW_ADMISSION', 'RE_ENROLLMENT', 'TRANSFER_IN')),
    CONSTRAINT chk_student_enrollments_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN', 'TRANSFERRED', 'CANCELLED')),
    CONSTRAINT chk_student_enrollments_dates
        CHECK (end_date IS NULL OR end_date >= enrollment_date)
);

CREATE UNIQUE INDEX uq_student_enrollments_active_per_year
    ON student_enrollments (tenant_id, student_id, academic_year_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_student_enrollments_tenant_id
    ON student_enrollments (tenant_id);
CREATE INDEX idx_student_enrollments_tenant_student_year
    ON student_enrollments (tenant_id, student_id, academic_year_id);
CREATE INDEX idx_student_enrollments_tenant_class_status
    ON student_enrollments (tenant_id, class_room_id, status);
CREATE INDEX idx_student_enrollments_tenant_year_status
    ON student_enrollments (tenant_id, academic_year_id, status);

-- 4. Familles / Foyers par établissement
CREATE TABLE families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    code VARCHAR(32),
    name VARCHAR(150) NOT NULL,
    primary_phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    city VARCHAR(100),
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_families_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_families_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_families_tenant_code
    ON families (tenant_id, code)
    WHERE code IS NOT NULL;

CREATE INDEX idx_families_tenant_id
    ON families (tenant_id);
CREATE INDEX idx_families_tenant_name
    ON families (tenant_id, name);

-- 5. Parents et tuteurs légaux par établissement
CREATE TABLE guardians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    family_id UUID,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    gender VARCHAR(20),
    phone VARCHAR(20) NOT NULL,
    secondary_phone VARCHAR(20),
    email VARCHAR(255),
    profession VARCHAR(120),
    address TEXT,
    national_id_number VARCHAR(60),
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'fr',
    user_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_guardians_family
        FOREIGN KEY (tenant_id, family_id) REFERENCES families(tenant_id, id),
    CONSTRAINT uq_guardians_tenant_id_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_guardians_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_guardians_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uq_guardians_tenant_user_id
    ON guardians (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_guardians_tenant_id
    ON guardians (tenant_id);
CREATE INDEX idx_guardians_tenant_family
    ON guardians (tenant_id, family_id);
CREATE INDEX idx_guardians_tenant_phone
    ON guardians (tenant_id, phone);
CREATE INDEX idx_guardians_tenant_name
    ON guardians (tenant_id, last_name, first_name);

-- 6. Liens élève <-> parent/tuteur légal
CREATE TABLE student_guardians (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES schools(id),
    student_id UUID NOT NULL,
    guardian_id UUID NOT NULL,
    relationship_type VARCHAR(30) NOT NULL,
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    financial_responsible BOOLEAN NOT NULL DEFAULT FALSE,
    legal_custody BOOLEAN NOT NULL DEFAULT FALSE,
    emergency_contact BOOLEAN NOT NULL DEFAULT FALSE,
    authorized_pickup BOOLEAN NOT NULL DEFAULT TRUE,
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_student_guardians_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES students(tenant_id, id),
    CONSTRAINT fk_student_guardians_guardian
        FOREIGN KEY (tenant_id, guardian_id) REFERENCES guardians(tenant_id, id),
    CONSTRAINT uq_student_guardians_tenant_student_guardian
        UNIQUE (tenant_id, student_id, guardian_id),
    CONSTRAINT chk_student_guardians_relationship
        CHECK (relationship_type IN (
            'FATHER',
            'MOTHER',
            'LEGAL_GUARDIAN',
            'GRANDPARENT',
            'UNCLE_AUNT',
            'SIBLING',
            'SPONSOR',
            'OTHER'
        ))
);

CREATE UNIQUE INDEX uq_student_guardians_primary_per_student
    ON student_guardians (tenant_id, student_id)
    WHERE active = TRUE AND primary_contact = TRUE;

CREATE UNIQUE INDEX uq_student_guardians_financial_per_student
    ON student_guardians (tenant_id, student_id)
    WHERE active = TRUE AND financial_responsible = TRUE;

CREATE INDEX idx_student_guardians_tenant_id
    ON student_guardians (tenant_id);
CREATE INDEX idx_student_guardians_tenant_student
    ON student_guardians (tenant_id, student_id);
CREATE INDEX idx_student_guardians_tenant_guardian
    ON student_guardians (tenant_id, guardian_id);

-- 7. Row-Level Security (RLS) activée et forcée sur toutes les nouvelles tables métier (fail-closed)
ALTER TABLE student_matricule_sequences ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_matricule_sequences FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_matricule_sequences ON student_matricule_sequences
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE students ENABLE ROW LEVEL SECURITY;
ALTER TABLE students FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_students ON students
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE student_enrollments ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_enrollments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_enrollments ON student_enrollments
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE families ENABLE ROW LEVEL SECURITY;
ALTER TABLE families FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_families ON families
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE guardians ENABLE ROW LEVEL SECURITY;
ALTER TABLE guardians FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_guardians ON guardians
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

ALTER TABLE student_guardians ENABLE ROW LEVEL SECURITY;
ALTER TABLE student_guardians FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_student_guardians ON student_guardians
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());

-- 8. Droits du rôle applicatif non-superuser soumis strictement à la RLS (NOBYPASSRLS)
GRANT SELECT, INSERT, UPDATE ON student_matricule_sequences TO ecole_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    students,
    student_enrollments,
    families,
    guardians,
    student_guardians
TO ecole_app;
