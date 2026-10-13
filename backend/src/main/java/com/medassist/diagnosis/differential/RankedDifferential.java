package com.medassist.diagnosis.differential;

import com.medassist.diagnosis.reasoning.ValidatedReasoning;

/** A differential in display order. Exactly one of {@code ai} / {@code knowledgeLinked} is set. */
public record RankedDifferential(int rank, String rankBasis, ValidatedReasoning.Differential ai,
                                 DifferentialCandidate knowledgeLinked) {

    public String conditionName() {
        return ai != null ? ai.conditionName() : knowledgeLinked.conditionName();
    }
}
