# ADR-007: Immutable assessment versions

**Status:** Accepted

## Context
Clinicians iterate: add an ECG, re-run; add troponin, re-run. Reviewers and auditors must be able to see exactly what
the system showed at each point, which inputs it used and what changed.

## Decision
* Each run produces a new `AssessmentVersion` (V1, V2, …) containing the fact snapshot, input hash, rule results,
  scores, alerts, evidence snapshot, differentials and AI execution reference.
* Versions and their child rows are append-only, enforced by database triggers and revoked privileges.
* A change set is computed against the previous version (input changes and output changes) and shown in the UI.
* Feedback references the version it was given on; the final assessment references the reviewed version.

## Consequences
* Full reproducibility and auditability of decision support over time.
* Storage grows with each run; acceptable for clinical data volumes, and AI raw responses have separate retention.
* Corrections are new versions or "entered in error" markers on source data — never in-place edits.
