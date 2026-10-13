package com.medassist.diagnosis.differential;

import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Generates differential candidates from the condition tags that knowledge managers attached to the documents whose
 * passages were retrieved for this encounter. Deterministic and fully traceable to sources.
 */
@Component
public class KnowledgeLinkedCandidateGenerator {

    static final int MAX_CANDIDATES = 8;

    public List<DifferentialCandidate> generate(List<RetrievedClinicalEvidence> evidence) {
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, List<String>> keys = new LinkedHashMap<>();
        Map<String, Double> best = new LinkedHashMap<>();
        for (RetrievedClinicalEvidence e : evidence) {
            for (String tag : e.conditionTags()) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String normalized = tag.trim().toLowerCase(Locale.ROOT);
                displayNames.putIfAbsent(normalized, tag.trim());
                keys.computeIfAbsent(normalized, k -> new ArrayList<>()).add(e.evidenceKey());
                best.merge(normalized, e.similarity(), Math::max);
            }
        }
        return displayNames.keySet().stream()
                .map(k -> new DifferentialCandidate(displayNames.get(k), keys.get(k), best.get(k)))
                .sorted(Comparator.comparingInt((DifferentialCandidate c) -> c.evidenceKeys().size()).reversed()
                        .thenComparing(Comparator.comparingDouble(DifferentialCandidate::bestSimilarity).reversed())
                        .thenComparing(DifferentialCandidate::conditionName))
                .limit(MAX_CANDIDATES)
                .toList();
    }
}
