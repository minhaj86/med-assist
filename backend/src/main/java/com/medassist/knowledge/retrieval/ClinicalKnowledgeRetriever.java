package com.medassist.knowledge.retrieval;

import java.util.List;

/**
 * Retrieves approved, enabled knowledge-base passages relevant to a clinical query. Results carry evidence keys
 * (K1..Kn) in rank order so downstream AI output can be validated against exactly these sources.
 */
public interface ClinicalKnowledgeRetriever {

    List<RetrievedClinicalEvidence> retrieve(KnowledgeQuery query);
}
