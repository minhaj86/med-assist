package com.medassist.diagnosis.differential;

import com.medassist.diagnosis.reasoning.ValidatedReasoning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic, documented ranking. Ranks express display priority for review - not likelihood.
 *
 * <ol>
 *   <li>AI-reasoned differentials: confidence level (HIGH, MODERATE, LOW), then number of validated supporting
 *       statements, then knowledge grounding, then the order proposed by the model.</li>
 *   <li>Knowledge-linked candidates not addressed by the AI (a safety net for conditions discussed in retrieved
 *       knowledge): number of supporting passages, then retrieval similarity.</li>
 * </ol>
 */
@Component
public class DifferentialRanker {

    static final String AI_BASIS = "Ordered by confidence level, then number of validated supporting findings, "
            + "then knowledge grounding (display order, not likelihood)";
    static final String KNOWLEDGE_BASIS = "Discussed in retrieved knowledge for these findings; not assessed by AI "
            + "(ordered by number of supporting passages)";

    public List<RankedDifferential> rank(ValidatedReasoning reasoning, List<DifferentialCandidate> candidates) {
        List<RankedDifferential> ranked = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        if (reasoning != null) {
            List<ValidatedReasoning.Differential> ai = new ArrayList<>(reasoning.differentials());
            List<ValidatedReasoning.Differential> original = List.copyOf(ai);
            ai.sort(Comparator
                    .comparingInt((ValidatedReasoning.Differential d) -> confidenceOrder(d.confidence()))
                    .thenComparing(Comparator.comparingLong(DifferentialRanker::supportedCount).reversed())
                    .thenComparingInt(d -> d.grounding().ordinal())
                    .thenComparingInt(original::indexOf));
            for (ValidatedReasoning.Differential d : ai) {
                ranked.add(new RankedDifferential(ranked.size() + 1, AI_BASIS, d, null));
                covered.add(d.conditionName().toLowerCase(Locale.ROOT));
            }
        }
        for (DifferentialCandidate c : candidates) {
            if (covered.add(c.conditionName().toLowerCase(Locale.ROOT))) {
                ranked.add(new RankedDifferential(ranked.size() + 1, KNOWLEDGE_BASIS, null, c));
            }
        }
        return List.copyOf(ranked);
    }

    private static int confidenceOrder(ConfidenceLevel level) {
        if (level == null) {
            return 3;
        }
        return switch (level) {
            case HIGH -> 0;
            case MODERATE -> 1;
            case LOW -> 2;
        };
    }

    private static long supportedCount(ValidatedReasoning.Differential d) {
        return d.supportingEvidence().stream().filter(s -> s.support() == SupportStatus.SUPPORTED).count();
    }
}
