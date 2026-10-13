package com.medassist.knowledge.retrieval;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A knowledge-base passage retrieved for a clinical question, with full source metadata.
 *
 * @param evidenceKey stable key within one request (K1, K2, ...) that AI output must cite
 * @param similarity  retrieval similarity (vector-space closeness). NOT a measure of medical confidence.
 */
public record RetrievedClinicalEvidence(String evidenceKey, UUID chunkId, UUID documentId, String documentTitle,
                                        String sourceOrganization, String section, Integer page, double similarity,
                                        String text, String documentVersion, LocalDate publicationDate,
                                        boolean syntheticSource, List<String> conditionTags) {

    public RetrievedClinicalEvidence {
        conditionTags = conditionTags == null ? List.of() : List.copyOf(conditionTags);
    }
}
