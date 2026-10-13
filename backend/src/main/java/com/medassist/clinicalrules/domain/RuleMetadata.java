package com.medassist.clinicalrules.domain;

import com.medassist.common.domain.ClinicalSeverity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable description of one version of a clinical rule (governance metadata included). */
public record RuleMetadata(UUID definitionId, String ruleId, String version, String name, RuleCategory category,
                           ClinicalSeverity severity, String logicKey, String description, String recommendedAction,
                           List<String> references, String source, String approvedBy, boolean demonstrationOnly,
                           Instant effectiveFrom, Instant effectiveUntil) {

    public RuleMetadata {
        references = references == null ? List.of() : List.copyOf(references);
    }
}
