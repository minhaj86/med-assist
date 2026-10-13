package com.medassist.clinicalrules.domain;

import tools.jackson.databind.JsonNode;

/**
 * Reviewed rule logic, identified by a stable key. A {@link RuleDefinition} selects the logic and supplies its
 * parameters (e.g. thresholds); binding validates the parameters and yields an executable {@link ClinicalRule}.
 */
public interface RuleLogic {

    String logicKey();

    /** @throws IllegalArgumentException if the parameters are invalid for this logic */
    ClinicalRule bind(RuleMetadata metadata, JsonNode parameters);
}
