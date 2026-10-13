package com.medassist.diagnosis.reasoning;

import java.util.List;
import java.util.UUID;

/**
 * Result of the AI reasoning step.
 *
 * @param reasoning        validated output; null unless status is COMPLETED
 * @param validationIssues technical validation findings (never raw model output)
 */
public record ReasoningOutcome(AiReasoningStatus status, String statusMessage, UUID aiExecutionId,
                               ValidatedReasoning reasoning, List<String> validationIssues) {

    public static ReasoningOutcome without(AiReasoningStatus status, String message) {
        return new ReasoningOutcome(status, message, null, null, List.of());
    }
}
