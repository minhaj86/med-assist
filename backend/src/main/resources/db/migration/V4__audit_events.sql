CREATE TABLE audit_events (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ NOT NULL,
    user_id        VARCHAR(100),
    patient_id     UUID,
    encounter_id   UUID,
    event_type     VARCHAR(60) NOT NULL,
    entity_type    VARCHAR(60),
    entity_id      VARCHAR(100),
    metadata       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    correlation_id VARCHAR(100),
    outcome        VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'
);
CREATE INDEX ix_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_patient ON audit_events (patient_id, occurred_at DESC);
CREATE INDEX ix_audit_encounter ON audit_events (encounter_id, occurred_at DESC);
CREATE INDEX ix_audit_type ON audit_events (event_type, occurred_at DESC);
CREATE INDEX ix_audit_user ON audit_events (user_id, occurred_at DESC);

-- The audit trail is append-only.
CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION medassist_reject_modification();
