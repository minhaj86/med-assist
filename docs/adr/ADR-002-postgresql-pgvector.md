# ADR-002: PostgreSQL + pgvector

**Status:** Accepted

## Context
Clinical data is relational and needs strong consistency, constraints and auditability. RAG needs vector similarity
search over knowledge chunks. A separate vector database would add another stateful system, another backup and
security boundary, and cross-store consistency problems (e.g. deleting a document and its vectors atomically).

## Decision
Use PostgreSQL for all data, with the `pgvector` extension for embeddings. Schema changes are managed by Flyway.
Embeddings are stored in an unconstrained `vector` column keyed by `(chunk_id, embedding_model)` and retrieval filters by
model and dimensions, so the embedding model can change without a schema migration (re-index instead).
Migrations run as a schema-owner role; the application connects as a DML-only role, and append-only tables are
protected by triggers and revoked privileges.

## Consequences
* Single datastore; document deletion removes chunks and embeddings transactionally (cascade).
* Exact cosine search is adequate for the expected corpus size; for large corpora add a partial HNSW index per
  model/dimension (documented in `docs/architecture.md`).
* Requires the pgvector extension (superuser to install) — handled by the container init script.
