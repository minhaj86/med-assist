package com.medassist.diagnosis.scoring;

import com.medassist.common.domain.ClinicalSeverity;
import java.util.List;
import java.util.Map;

/**
 * Result of a clinical score calculation, including full calculation transparency.
 *
 * @param score          null when too few variables are available to calculate a meaningful value
 * @param alertSeverity  optional alert raised by the calculator (null = no alert)
 */
public record ScoreResult(String scoreId, String name, String version, Integer score, Integer maxScore,
                          List<ScoreComponent> calculationDetails, Map<String, String> inputVariables,
                          List<String> missingVariables, String interpretation, String reference,
                          boolean demonstrationOnly, ClinicalSeverity alertSeverity, String alertMessage) {

    public ScoreResult {
        calculationDetails = List.copyOf(calculationDetails);
        inputVariables = Map.copyOf(inputVariables);
        missingVariables = List.copyOf(missingVariables);
    }
}
