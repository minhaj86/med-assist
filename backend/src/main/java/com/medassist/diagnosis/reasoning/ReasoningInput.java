package com.medassist.diagnosis.reasoning;

import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.differential.DifferentialCandidate;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import java.util.List;
import java.util.UUID;

/** Everything the reasoning step may use: pseudonymized facts, deterministic results and retrieved evidence. */
public record ReasoningInput(UUID runId, String requestedBy, ClinicalContext context, RuleEvaluationReport rules,
                             List<ScoreResult> scores, List<RetrievedClinicalEvidence> evidence,
                             List<DifferentialCandidate> candidates) {
}
