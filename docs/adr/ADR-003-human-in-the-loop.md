# ADR-003: Human-in-the-loop clinical workflow

**Status:** Accepted

## Context
MedAssist is clinical decision support, not autonomous diagnosis. AI output can be wrong, incomplete or biased.
Regulatory expectations and patient safety require that a qualified clinician makes and owns every clinical decision.

## Decision
* Every AI suggestion (differential, red flag, investigation) starts as `AI_GENERATED` and moves through explicit
  clinician actions (`REVIEWED`, `ACCEPTED`, `REJECTED`, `MODIFIED`) recorded as append-only `ClinicianFeedback`
  (`ACCEPTED`, `REJECTED`, `UNCERTAIN`, `NOT_RELEVANT`, `NEEDS_MORE_INFORMATION`, optional reason).
* The final clinical assessment is a separate, explicit, attested clinician action (`FINALIZED`); nothing is finalized
  automatically.
* AI can only *propose* record updates (assistant proposals); a proposal is saved only when a clinician reviews and
  confirms it in a form, and the record then carries `AI_INFERRED` provenance plus the confirming clinician.
* AI never orders investigations; suggested investigations are separate from the ordered/completed workflow.
* Feedback is stored for evaluation and controlled research only — never used for automatic model training.
* UI language is hedged ("possible differential", "potential consideration") and a disclaimer is always visible.

## Consequences
* Clear accountability and auditability of every clinical decision.
* Extra clicks for clinicians; mitigated with keyboard shortcuts and dense layouts.
* Analytics on acceptance/rejection rates are possible without feeding back into models.
