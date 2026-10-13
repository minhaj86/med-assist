package com.medassist.knowledge.retrieval;

import java.util.List;

/** Inputs for clinical knowledge retrieval. */
public record KnowledgeQuery(List<String> symptoms, List<String> observations, List<String> suspectedConditions,
                             String clinicianQuestion, int maxResults) {

    public KnowledgeQuery {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        observations = observations == null ? List.of() : List.copyOf(observations);
        suspectedConditions = suspectedConditions == null ? List.of() : List.copyOf(suspectedConditions);
    }

    public String toSearchText() {
        StringBuilder sb = new StringBuilder();
        if (clinicianQuestion != null && !clinicianQuestion.isBlank()) {
            sb.append(clinicianQuestion).append(". ");
        }
        if (!symptoms.isEmpty()) {
            sb.append(String.join(", ", symptoms)).append(". ");
        }
        if (!observations.isEmpty()) {
            sb.append(String.join(", ", observations)).append(". ");
        }
        if (!suspectedConditions.isEmpty()) {
            sb.append(String.join(", ", suspectedConditions)).append('.');
        }
        return sb.toString().trim();
    }
}
