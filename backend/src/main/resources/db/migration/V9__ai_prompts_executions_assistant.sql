-- Prompt registry: template text is immutable per version; only the status may change.
CREATE TABLE prompt_templates (
    id               UUID PRIMARY KEY,
    prompt_id        VARCHAR(100)  NOT NULL,
    version          VARCHAR(30)   NOT NULL,
    description      VARCHAR(1000),
    system_template  TEXT          NOT NULL,
    user_template    TEXT          NOT NULL,
    repair_template  TEXT          NOT NULL,
    intended_model   VARCHAR(300),
    output_schema_id VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    checksum         VARCHAR(64)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    created_by       VARCHAR(100)  NOT NULL,
    CONSTRAINT uq_prompt_version UNIQUE (prompt_id, version)
);
CREATE UNIQUE INDEX ux_prompt_single_active ON prompt_templates (prompt_id) WHERE status = 'ACTIVE';

CREATE OR REPLACE FUNCTION medassist_protect_prompt_template() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Prompt templates cannot be deleted' USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.prompt_id IS DISTINCT FROM OLD.prompt_id OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.system_template IS DISTINCT FROM OLD.system_template OR NEW.user_template IS DISTINCT FROM OLD.user_template
        OR NEW.repair_template IS DISTINCT FROM OLD.repair_template
        OR NEW.output_schema_id IS DISTINCT FROM OLD.output_schema_id OR NEW.checksum IS DISTINCT FROM OLD.checksum THEN
        RAISE EXCEPTION 'Prompt template versions are immutable' USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER prompt_templates_protect BEFORE UPDATE OR DELETE ON prompt_templates
    FOR EACH ROW EXECUTE FUNCTION medassist_protect_prompt_template();

-- Every AI call: provider, model, prompt version, temperature, retrieved evidence and validation outcome.
CREATE TABLE ai_executions (
    id                    UUID PRIMARY KEY,
    purpose               VARCHAR(40)  NOT NULL,
    encounter_id          UUID,
    patient_id            UUID,
    assessment_run_id     UUID,
    provider              VARCHAR(50)  NOT NULL,
    requested_model       VARCHAR(200),
    reported_model        VARCHAR(200),
    temperature           NUMERIC(4, 2) NOT NULL,
    prompt_id             VARCHAR(100) NOT NULL,
    prompt_version        VARCHAR(30)  NOT NULL,
    prompt_checksum       VARCHAR(64)  NOT NULL,
    output_schema_id      VARCHAR(100) NOT NULL,
    started_at            TIMESTAMPTZ  NOT NULL,
    completed_at          TIMESTAMPTZ  NOT NULL,
    duration_ms           BIGINT       NOT NULL,
    attempts              INTEGER      NOT NULL,
    status                VARCHAR(40)  NOT NULL,
    error_category        VARCHAR(40),
    retrieved_evidence    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    validation_issues     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    validation_summary    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    prompt_tokens         INTEGER,
    completion_tokens     INTEGER,
    raw_response          TEXT,
    raw_response_purged   BOOLEAN      NOT NULL DEFAULT FALSE,
    requested_by          VARCHAR(100) NOT NULL,
    correlation_id        VARCHAR(100)
);
CREATE INDEX ix_ai_executions_started ON ai_executions (started_at DESC);
CREATE INDEX ix_ai_executions_encounter ON ai_executions (encounter_id, started_at DESC);
CREATE INDEX ix_ai_executions_status ON ai_executions (status, started_at DESC);

CREATE TABLE assistant_conversations (
    id           UUID PRIMARY KEY,
    encounter_id UUID         NOT NULL REFERENCES encounters (id),
    patient_id   UUID         NOT NULL REFERENCES patients (id),
    started_by   VARCHAR(100) NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_assistant_conversations_encounter ON assistant_conversations (encounter_id, started_at DESC);

CREATE TABLE assistant_messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID          NOT NULL REFERENCES assistant_conversations (id),
    role            VARCHAR(20)   NOT NULL,
    content         TEXT          NOT NULL,
    structured      JSONB,
    ai_execution_id UUID,
    created_by      VARCHAR(100)  NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    content_purged  BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_assistant_messages_conversation ON assistant_messages (conversation_id, created_at);
