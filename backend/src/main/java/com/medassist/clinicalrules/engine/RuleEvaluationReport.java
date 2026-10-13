package com.medassist.clinicalrules.engine;

import com.medassist.clinicalrules.domain.RuleResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Results of one rule-engine run, including exactly which rule versions were executed and which rules were
 * skipped because an administrator disabled them.
 */
public record RuleEvaluationReport(Instant evaluatedAt, List<RuleResult> results, List<ExecutedRule> executedRules,
                                   List<String> disabledRuleIds, Duration duration) {

    public record ExecutedRule(UUID definitionId, String ruleId, String version) {
    }

    public List<RuleResult> triggered() {
        return results.stream().filter(r -> r.triggered() || r.evaluationError()).toList();
    }
}
