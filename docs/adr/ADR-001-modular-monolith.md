# ADR-001: Modular monolith

**Status:** Accepted

## Context
MedAssist needs clear boundaries between the clinical record, decision support, knowledge management, AI and platform
concerns. The team and the domain are still evolving; operational simplicity and transactional consistency for clinical
data matter more than independent scaling. Premature microservices would add distributed transactions, network failure
modes and deployment overhead without clear benefit.

## Decision
Build a single Spring Boot application organised as modules (`patient`, `encounter`, `assessment`, `clinicalrules`,
`knowledge`, `ai`, `fhir`, `audit`, …). Each module follows controller → application service → domain →
repository/adapter. Modules reference other aggregates **by id only**, never via JPA associations across modules, and
cross-module collaboration happens through application services or ports (e.g. `ClinicalReasoningPort` implemented by
the `ai` module and consumed optionally by `assessment`).

## Consequences
* One deployable, one database, local transactions; simple local development (`docker compose up`).
* Module boundaries are explicit and testable; modules such as `knowledge` or `ai` can later be extracted behind their
  existing ports with an HTTP or messaging adapter.
* Discipline is required to keep boundaries (no cross-module entity references); reviewers should reject shortcuts.
* Long-running AI work runs asynchronously in-process (virtual threads) rather than on a separate worker service.
