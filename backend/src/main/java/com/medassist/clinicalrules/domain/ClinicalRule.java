package com.medassist.clinicalrules.domain;

import com.medassist.diagnosis.context.ClinicalContext;

/**
 * A deterministic clinical safety rule. Implementations must be pure functions of the context: no I/O, no AI,
 * no randomness. Each rule instance is bound to exactly one reviewed, versioned rule definition.
 */
public interface ClinicalRule {

    RuleMetadata metadata();

    RuleResult evaluate(ClinicalContext context);
}
