CREATE TABLE users (
    id              VARCHAR(100) PRIMARY KEY,
    username        VARCHAR(200) NOT NULL,
    display_name    VARCHAR(200),
    email           VARCHAR(320),
    roles           VARCHAR(500) NOT NULL DEFAULT '',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    first_seen_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE patients (
    id                  UUID PRIMARY KEY,
    external_patient_id VARCHAR(64)  NOT NULL UNIQUE,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    biological_sex      VARCHAR(20)  NOT NULL,
    gender              VARCHAR(50),
    contact_phone       VARCHAR(40),
    synthetic           BOOLEAN      NOT NULL DEFAULT FALSE,
    provenance          VARCHAR(30)  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_patients_provenance CHECK (provenance <> 'AI_INFERRED')
);
CREATE INDEX ix_patients_last_name ON patients (lower(last_name));
CREATE INDEX ix_patients_first_name ON patients (lower(first_name));
CREATE INDEX ix_patients_updated_at ON patients (updated_at DESC);

CREATE TABLE encounters (
    id              UUID PRIMARY KEY,
    patient_id      UUID         NOT NULL REFERENCES patients (id),
    encounter_type  VARCHAR(30)  NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    chief_complaint VARCHAR(500),
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    clinician_id    VARCHAR(100) NOT NULL,
    provenance      VARCHAR(30)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_encounters_completed CHECK (completed_at IS NULL OR completed_at >= started_at)
);
CREATE INDEX ix_encounters_patient ON encounters (patient_id, started_at DESC);
CREATE INDEX ix_encounters_clinician ON encounters (clinician_id, started_at DESC);
