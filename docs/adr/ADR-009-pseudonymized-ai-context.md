# ADR-009: Pseudonymized AI context with citation keys

**Status:** Accepted

## Context
Sending identifiable patient data to an external model increases privacy risk. Citations need stable, verifiable
references to the facts and passages the model saw.

## Decision
* The `ClinicalContextAssembler` builds a minimal, pseudonymized context: age band/years, biological sex and clinical
  facts — no names, external identifiers, contact details or dates of birth. Free text is scrubbed of identifiers.
* Each fact receives a key `F1…Fn`; each retrieved passage a key `K1…Kn`. Prompts instruct the model to cite these
  keys, and validators accept only keys that exist in the request.
* Audit and logs record ids, counts and versions — not clinical values or prompts.

## Consequences
* Lower exposure when using cloud providers; local models remain an option for stricter policies.
* The model cannot use identity-dependent information (by design).
* Fact keys are per-version; the UI resolves them using the version's stored fact snapshot.
