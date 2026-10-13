package com.medassist.clinicalrules.domain;

import com.medassist.common.domain.ClinicalSeverity;
import java.util.List;

/**
 * Outcome of evaluating one deterministic clinical rule. Rule results are produced without any AI involvement.
 *
 * @param evaluationError true when the rule could not be evaluated (e.g. invalid configuration) - surfaced to the
 *                        clinician rather than silently ignored
 */
public record RuleResult(String ruleId, String ruleVersion, String ruleName, ClinicalSeverity severity,
                         boolean triggered, String explanation, List<SupportingObservation> supportingObservations,
                         String recommendedAction, List<String> references, boolean demonstrationOnly,
                         boolean evaluationError) {

    public RuleResult {
        supportingObservations = supportingObservations == null ? List.of() : List.copyOf(supportingObservations);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static RuleResult triggered(RuleMetadata m, String explanation, List<SupportingObservation> support) {
        return new RuleResult(m.ruleId(), m.version(), m.name(), m.severity(), true, explanation, support,
                m.recommendedAction(), m.references(), m.demonstrationOnly(), false);
    }

    public static RuleResult notTriggered(RuleMetadata m, String explanation, List<SupportingObservation> examined) {
        return new RuleResult(m.ruleId(), m.version(), m.name(), m.severity(), false, explanation, examined,
                m.recommendedAction(), m.references(), m.demonstrationOnly(), false);
    }

    public static RuleResult error(RuleMetadata m, String explanation) {
        return new RuleResult(m.ruleId(), m.version(), m.name(), ClinicalSeverity.WARNING, false, explanation,
                List.of(), "Rule could not be evaluated. Notify a system administrator; do not rely on this rule.",
                m.references(), m.demonstrationOnly(), true);
    }
}
