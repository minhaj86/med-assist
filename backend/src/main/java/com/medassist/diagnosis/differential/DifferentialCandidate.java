package com.medassist.diagnosis.differential;

import java.util.List;

/** A condition worth considering, generated deterministically from retrieved knowledge (not from the AI). */
public record DifferentialCandidate(String conditionName, List<String> evidenceKeys, double bestSimilarity) {

    public DifferentialCandidate {
        evidenceKeys = List.copyOf(evidenceKeys);
    }
}
