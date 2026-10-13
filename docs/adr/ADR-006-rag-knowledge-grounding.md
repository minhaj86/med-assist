# ADR-006: RAG-based medical knowledge grounding

**Status:** Accepted

## Context
LLMs hallucinate and their training data is unversioned and unapproved. Clinical reasoning must be traceable to
approved, versioned sources chosen by the organisation.

## Decision
* Knowledge managers upload approved documents (PDF, TXT, Markdown or manual entry) with source, version, publication
  and review dates, jurisdiction and approval status.
* Ingestion: extraction → cleanup → section detection → chunking (configurable size/overlap) → metadata enrichment →
  embeddings → pgvector.
* Only documents that are `APPROVED`, enabled and indexed with the current embedding model are retrievable.
* Retrieved passages are given to the model as evidence keys `K1…Kn`; every substantive statement must cite them (or
  clinical fact keys). The backend verifies citations against the evidence actually supplied; invalid citations are
  removed and uncited statements marked `UNSUPPORTED`. Differentials without valid knowledge citations are
  `UNGROUNDED` and their confidence is capped at `LOW`.
* The model may not produce titles, URLs, authors or guideline names; guardrails withhold such text.
* Similarity scores are labelled "retrieval similarity" and never presented as clinical confidence.

## Consequences
* Answers are only as good as the curated corpus; empty or unapproved corpora lead to explicit
  "insufficient information" outcomes rather than invented facts.
* Assessment versions snapshot the evidence they used, so later document deletion does not alter history.
* The built-in `local-hashing` embedder is lexical and for development/testing only; production should configure a
  semantic embedding model.
