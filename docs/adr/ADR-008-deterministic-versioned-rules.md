# ADR-008: Deterministic, versioned safety rules independent of AI

**Status:** Accepted

## Context
Critical findings (e.g. severe hypoxia) must be flagged reliably even when the AI provider is down, slow or wrong.
Rule thresholds are clinical content that must be reviewed, approved and traceable.

## Decision
* Implement `ClinicalRule.evaluate(ClinicalContext) → RuleResult` executed by a `ClinicalRuleEngine` that never calls AI.
* Rule definitions are data: id, version, parameters, severity, source, `approvedBy`, `effectiveFrom`/`effectiveUntil`,
  demonstration flag. Definitions are immutable; changes publish a new version that supersedes the old one from its
  effective date. Administrators can enable/disable rules (audited, reason required).
* A live safety endpoint evaluates rules and scores on current data without running an assessment.
* Shipped rules are **demonstration rules** with illustrative thresholds, clearly labelled as not validated clinical
  guidance.
* The UI renders rule/score alerts with a distinct visual treatment and origin label so they cannot be mistaken for AI.

## Consequences
* Safety alerts remain available during AI outages.
* Clinical governance is needed to replace demonstration rules with approved content.
* Rule evaluation errors are isolated per rule and reported, never silently ignored.
