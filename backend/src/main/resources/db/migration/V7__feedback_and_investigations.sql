-- Clinician feedback on suggestions. Append-only; NOT used for model training (stored for evaluation,
-- product analytics and controlled future research only).
CREATE TABLE clinician_feedback (
    id                    UUID PRIMARY KEY,
    assessment_version_id UUID          NOT NULL REFERENCES assessment_versions (id),
    suggestion_type       VARCHAR(30)   NOT NULL,
    suggestion_id         UUID          NOT NULL,
    clinician_id          VARCHAR(100)  NOT NULL,
    action                VARCHAR(40)   NOT NULL,
    reason                VARCHAR(2000),
    modified_text         VARCHAR(2000),
    created_at            TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_feedback_version ON clinician_feedback (assessment_version_id, suggestion_id, created_at DESC);
CREATE TRIGGER clinician_feedback_append_only BEFORE UPDATE OR DELETE ON clinician_feedback
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();

-- Investigation workflow (separate from immutable suggestions). Only clinicians change status; AI never orders.
CREATE TABLE investigations (
    id                           UUID PRIMARY KEY,
    encounter_id                 UUID          NOT NULL REFERENCES encounters (id),
    patient_id                   UUID          NOT NULL REFERENCES patients (id),
    name                         VARCHAR(300)  NOT NULL,
    proposed_by                  VARCHAR(20)   NOT NULL,
    reason                       VARCHAR(2000),
    supporting_evidence          JSONB         NOT NULL DEFAULT '[]'::jsonb,
    status                       VARCHAR(20)   NOT NULL,
    source_suggestion_id         UUID,
    source_assessment_version_id UUID,
    created_by                   VARCHAR(100)  NOT NULL,
    created_at                   TIMESTAMPTZ   NOT NULL,
    updated_at                   TIMESTAMPTZ   NOT NULL,
    version                      BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_investigations_encounter ON investigations (encounter_id);
CREATE UNIQUE INDEX ux_investigations_suggestion ON investigations (encounter_id, source_suggestion_id)
    WHERE source_suggestion_id IS NOT NULL;

CREATE TABLE investigation_status_history (
    id               UUID PRIMARY KEY,
    investigation_id UUID         NOT NULL REFERENCES investigations (id),
    from_status      VARCHAR(20),
    to_status        VARCHAR(20)  NOT NULL,
    changed_by       VARCHAR(100) NOT NULL,
    changed_at       TIMESTAMPTZ  NOT NULL,
    note             VARCHAR(1000)
);
CREATE INDEX ix_investigation_history ON investigation_status_history (investigation_id, changed_at);
CREATE TRIGGER investigation_status_history_append_only BEFORE UPDATE OR DELETE ON investigation_status_history
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
