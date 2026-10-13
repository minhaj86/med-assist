CREATE TABLE assessments (
    id                    UUID PRIMARY KEY,
    encounter_id          UUID        NOT NULL UNIQUE REFERENCES encounters (id),
    patient_id            UUID        NOT NULL REFERENCES patients (id),
    workflow_status       VARCHAR(30) NOT NULL,
    latest_version_number INTEGER     NOT NULL DEFAULT 0,
    final_summary         VARCHAR(4000),
    final_plan            VARCHAR(4000),
    final_diagnoses       JSONB,
    finalized_version_id  UUID,
    finalized_by          VARCHAR(100),
    finalized_at          TIMESTAMPTZ,
    clinician_attestation BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_assessment_finalized CHECK (
        workflow_status <> 'FINALIZED'
        OR (finalized_by IS NOT NULL AND finalized_at IS NOT NULL AND clinician_attestation))
);

CREATE TABLE assessment_runs (
    id             UUID PRIMARY KEY,
    assessment_id  UUID         NOT NULL REFERENCES assessments (id),
    encounter_id   UUID         NOT NULL REFERENCES encounters (id),
    patient_id     UUID         NOT NULL REFERENCES patients (id),
    status         VARCHAR(20)  NOT NULL,
    current_stage  VARCHAR(40)  NOT NULL,
    stage_history  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    include_ai     BOOLEAN      NOT NULL,
    requested_by   VARCHAR(100) NOT NULL,
    requested_at   TIMESTAMPTZ  NOT NULL,
    completed_at   TIMESTAMPTZ,
    version_id     UUID,
    failure_reason VARCHAR(500),
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_assessment_runs_encounter ON assessment_runs (encounter_id, requested_at DESC);

-- Immutable assessment versions and their results.
CREATE TABLE assessment_versions (
    id                    UUID PRIMARY KEY,
    assessment_id         UUID         NOT NULL REFERENCES assessments (id),
    encounter_id          UUID         NOT NULL REFERENCES encounters (id),
    patient_id            UUID         NOT NULL REFERENCES patients (id),
    version_number        INTEGER      NOT NULL,
    run_id                UUID         NOT NULL REFERENCES assessment_runs (id),
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    input_snapshot        JSONB        NOT NULL,
    input_hash            VARCHAR(64)  NOT NULL,
    changes_from_previous JSONB        NOT NULL,
    ai_status             VARCHAR(30)  NOT NULL,
    ai_status_message     VARCHAR(500),
    ai_execution_id       UUID,
    uncertainties         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    additional_questions  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    validation_issues     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    data_quality_warnings JSONB        NOT NULL DEFAULT '[]'::jsonb,
    executed_rules        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    disabled_rules        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT uq_assessment_version UNIQUE (assessment_id, version_number)
);
CREATE INDEX ix_assessment_versions_encounter ON assessment_versions (encounter_id, version_number DESC);
CREATE INDEX ix_assessment_versions_created ON assessment_versions (created_at DESC);

CREATE TABLE clinical_rule_results (
    id                      UUID PRIMARY KEY,
    assessment_version_id   UUID          NOT NULL REFERENCES assessment_versions (id),
    rule_definition_id      UUID          NOT NULL REFERENCES clinical_rule_definitions (id),
    rule_id                 VARCHAR(60)   NOT NULL,
    rule_version            VARCHAR(30)   NOT NULL,
    rule_name               VARCHAR(200)  NOT NULL,
    severity                VARCHAR(20)   NOT NULL,
    triggered               BOOLEAN       NOT NULL,
    evaluation_error        BOOLEAN       NOT NULL,
    explanation             VARCHAR(2000) NOT NULL,
    supporting_observations JSONB         NOT NULL,
    recommended_action      VARCHAR(1000),
    rule_references         JSONB         NOT NULL,
    demonstration_only      BOOLEAN       NOT NULL,
    display_order           INTEGER       NOT NULL
);
CREATE INDEX ix_rule_results_version ON clinical_rule_results (assessment_version_id);

CREATE TABLE clinical_score_results (
    id                    UUID PRIMARY KEY,
    assessment_version_id UUID        NOT NULL REFERENCES assessment_versions (id),
    score_id              VARCHAR(60) NOT NULL,
    score_version         VARCHAR(30) NOT NULL,
    result                JSONB       NOT NULL,
    display_order         INTEGER     NOT NULL
);
CREATE INDEX ix_score_results_version ON clinical_score_results (assessment_version_id);

CREATE TABLE safety_alerts (
    id                    UUID PRIMARY KEY,
    assessment_version_id UUID         NOT NULL REFERENCES assessment_versions (id),
    origin                VARCHAR(20)  NOT NULL,
    severity              VARCHAR(20)  NOT NULL,
    title                 VARCHAR(300) NOT NULL,
    explanation           VARCHAR(2000),
    recommended_action    VARCHAR(1000),
    source_reference      VARCHAR(200),
    supporting_fact_keys  JSONB        NOT NULL,
    evidence_keys         JSONB        NOT NULL,
    support_status        VARCHAR(20)  NOT NULL,
    demonstration_only    BOOLEAN      NOT NULL,
    display_order         INTEGER      NOT NULL
);
CREATE INDEX ix_safety_alerts_version ON safety_alerts (assessment_version_id);
CREATE INDEX ix_safety_alerts_severity ON safety_alerts (severity);

-- Evidence is stored by value (no FK to knowledge tables) so versions stay reproducible after document removal.
CREATE TABLE retrieved_evidence (
    id                    UUID PRIMARY KEY,
    assessment_version_id UUID          NOT NULL REFERENCES assessment_versions (id),
    evidence_key          VARCHAR(10)   NOT NULL,
    chunk_id              UUID          NOT NULL,
    document_id           UUID          NOT NULL,
    document_title        VARCHAR(300)  NOT NULL,
    document_version      VARCHAR(50),
    source_organization   VARCHAR(300),
    section               VARCHAR(300),
    page_number           INTEGER,
    publication_date      DATE,
    similarity            NUMERIC(6, 5) NOT NULL,
    excerpt               TEXT          NOT NULL,
    synthetic_source      BOOLEAN       NOT NULL,
    display_order         INTEGER       NOT NULL
);
CREATE INDEX ix_retrieved_evidence_version ON retrieved_evidence (assessment_version_id);

CREATE TABLE differential_diagnoses (
    id                      UUID PRIMARY KEY,
    assessment_version_id   UUID          NOT NULL REFERENCES assessment_versions (id),
    rank                    INTEGER       NOT NULL,
    condition_name          VARCHAR(300)  NOT NULL,
    coding_system           VARCHAR(30),
    condition_code          VARCHAR(50),
    code_verification       VARCHAR(40)   NOT NULL,
    confidence_level        VARCHAR(20),
    original_confidence     VARCHAR(20),
    assessment_method       VARCHAR(30)   NOT NULL,
    grounding_status        VARCHAR(30)   NOT NULL,
    why_considered          VARCHAR(2000),
    distinguishing_features VARCHAR(2000),
    reasoning_summary       VARCHAR(4000),
    rank_basis              VARCHAR(300),
    citations               JSONB         NOT NULL,
    validation_notes        JSONB         NOT NULL,
    CONSTRAINT ck_differential_code CHECK (condition_code IS NULL OR coding_system IS NOT NULL)
);
CREATE INDEX ix_differentials_version ON differential_diagnoses (assessment_version_id);

CREATE TABLE diagnostic_evidence (
    id              UUID PRIMARY KEY,
    differential_id UUID          NOT NULL REFERENCES differential_diagnoses (id),
    evidence_type   VARCHAR(30)   NOT NULL,
    statement       VARCHAR(2000) NOT NULL,
    fact_keys       JSONB         NOT NULL,
    evidence_keys   JSONB         NOT NULL,
    support_status  VARCHAR(20)   NOT NULL,
    display_order   INTEGER       NOT NULL
);
CREATE INDEX ix_diagnostic_evidence_differential ON diagnostic_evidence (differential_id);

CREATE TABLE suggested_investigations (
    id                       UUID PRIMARY KEY,
    differential_id          UUID          NOT NULL REFERENCES differential_diagnoses (id),
    name                     VARCHAR(300)  NOT NULL,
    reason                   VARCHAR(2000),
    proposed_by              VARCHAR(20)   NOT NULL,
    supporting_evidence_keys JSONB         NOT NULL,
    support_status           VARCHAR(20)   NOT NULL,
    display_order            INTEGER       NOT NULL
);
CREATE INDEX ix_suggested_investigations_differential ON suggested_investigations (differential_id);

-- Immutability of assessment versions and everything they contain.
CREATE TRIGGER assessment_versions_immutable BEFORE UPDATE OR DELETE ON assessment_versions
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER clinical_rule_results_immutable BEFORE UPDATE OR DELETE ON clinical_rule_results
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER clinical_score_results_immutable BEFORE UPDATE OR DELETE ON clinical_score_results
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER safety_alerts_immutable BEFORE UPDATE OR DELETE ON safety_alerts
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER retrieved_evidence_immutable BEFORE UPDATE OR DELETE ON retrieved_evidence
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER differential_diagnoses_immutable BEFORE UPDATE OR DELETE ON differential_diagnoses
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER diagnostic_evidence_immutable BEFORE UPDATE OR DELETE ON diagnostic_evidence
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
CREATE TRIGGER suggested_investigations_immutable BEFORE UPDATE OR DELETE ON suggested_investigations
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
