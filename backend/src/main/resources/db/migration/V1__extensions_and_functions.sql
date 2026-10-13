-- pgvector for knowledge-base embeddings. In containerized deployments the extension is created by the
-- database init script (superuser); IF NOT EXISTS makes this a no-op there.
CREATE EXTENSION IF NOT EXISTS vector;

-- Guard used by append-only / immutable tables (audit trail, assessment versions, feedback).
CREATE OR REPLACE FUNCTION medassist_reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;
