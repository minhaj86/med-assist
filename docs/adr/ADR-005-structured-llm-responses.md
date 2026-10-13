# ADR-005: Structured, validated LLM responses

**Status:** Accepted

## Context
Free-text LLM output cannot be reliably checked, rendered or audited, and may contain definitive diagnostic language,
fabricated references or invented terminology codes.

## Decision
* Prompts are managed centrally (`prompts/catalog.json` + versioned template files), checksummed, registered in the
  database as immutable versions, and executed at low temperature.
* The model must return JSON conforming to a versioned JSON Schema (`ai/schemas/*.schema.json`).
* Every response passes: schema validation (draft 2020-12) → Jakarta bean validation → citation validation →
  terminology validation → language guardrails → grounding check. One repair attempt is allowed with a dedicated
  repair prompt; otherwise the output is rejected.
* Malformed output never reaches the clinician. Failures are audited (`AI_RESPONSE_REJECTED`) with the technical reason
  and shown as a calm, non-alarming status with safe retry.
* Each execution records prompt id/version, provider, requested/reported model, temperature, retrieved documents,
  validation status and token usage; raw responses are retained for a configurable period only.
* Hidden chain-of-thought is neither requested nor stored; the schema asks for concise clinician-facing summaries.

## Consequences
* Deterministic, testable contract (golden prompt tests, schema tests, scripted provider in CI).
* Some valid-but-unstructured answers are discarded; acceptable for safety.
* Schema changes require a new schema and prompt version.
