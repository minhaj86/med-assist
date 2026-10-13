CREATE TABLE knowledge_documents (
    id                  UUID PRIMARY KEY,
    series_id           UUID          NOT NULL,
    title               VARCHAR(300)  NOT NULL,
    document_type       VARCHAR(40)   NOT NULL,
    source_organization VARCHAR(300)  NOT NULL,
    source_url          VARCHAR(1000),
    jurisdiction        VARCHAR(100),
    version             VARCHAR(50)   NOT NULL,
    publication_date    DATE,
    review_date         DATE,
    approval_status     VARCHAR(30)   NOT NULL,
    enabled             BOOLEAN       NOT NULL,
    checksum            VARCHAR(64)   NOT NULL,
    content_type        VARCHAR(100)  NOT NULL,
    original_filename   VARCHAR(300),
    size_bytes          BIGINT        NOT NULL,
    ingestion_status    VARCHAR(30)   NOT NULL,
    ingestion_error     VARCHAR(1000),
    last_indexed_at     TIMESTAMPTZ,
    embedding_model     VARCHAR(200),
    chunk_count         INTEGER       NOT NULL DEFAULT 0,
    page_count          INTEGER,
    extracted_text      TEXT,
    condition_tags      JSONB         NOT NULL DEFAULT '[]'::jsonb,
    synthetic           BOOLEAN       NOT NULL DEFAULT FALSE,
    supersedes_id       UUID REFERENCES knowledge_documents (id) ON DELETE SET NULL,
    superseded_by_id    UUID REFERENCES knowledge_documents (id) ON DELETE SET NULL,
    uploaded_at         TIMESTAMPTZ   NOT NULL,
    uploaded_by         VARCHAR(100)  NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version_lock        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_knowledge_series_version UNIQUE (series_id, version)
);
CREATE INDEX ix_knowledge_documents_checksum ON knowledge_documents (checksum);
CREATE INDEX ix_knowledge_documents_series ON knowledge_documents (series_id, uploaded_at DESC);

CREATE TABLE knowledge_document_sources (
    document_id UUID PRIMARY KEY REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    content     BYTEA NOT NULL
);

CREATE TABLE knowledge_chunks (
    id             UUID PRIMARY KEY,
    document_id    UUID         NOT NULL REFERENCES knowledge_documents (id) ON DELETE CASCADE,
    chunk_index    INTEGER      NOT NULL,
    section        VARCHAR(300),
    page_number    INTEGER,
    content        TEXT         NOT NULL,
    char_count     INTEGER      NOT NULL,
    token_estimate INTEGER      NOT NULL,
    metadata       JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_knowledge_chunk_index UNIQUE (document_id, chunk_index)
);

-- Unconstrained vector column: vectors from different embedding models coexist and are always filtered by model.
CREATE TABLE knowledge_embeddings (
    chunk_id        UUID         NOT NULL REFERENCES knowledge_chunks (id) ON DELETE CASCADE,
    embedding_model VARCHAR(200) NOT NULL,
    dimensions      INTEGER      NOT NULL,
    embedding       vector       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (chunk_id, embedding_model),
    CONSTRAINT ck_embedding_dimensions CHECK (vector_dims(embedding) = dimensions)
);
CREATE INDEX ix_knowledge_embeddings_model ON knowledge_embeddings (embedding_model, dimensions);
