package com.medassist.ai.assistant;

import com.medassist.ai.validation.AssistantOutputContract;
import com.medassist.diagnosis.differential.SupportStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Validated assistant answer returned to the clinician. */
public record AssistantAnswer(UUID conversationId, UUID messageId, Status status, String message, String answer,
                              List<Statement> statements, boolean insufficientInformation,
                              List<ProposedUpdate> proposedUpdates, List<String> followUpQuestions,
                              List<Evidence> evidence, UUID aiExecutionId, Instant createdAt) {

    public enum Status {
        ANSWERED, OUTPUT_REJECTED
    }

    public record Statement(String text, List<String> factKeys, List<String> evidenceKeys, SupportStatus support) {
    }

    /** A proposal only: nothing is recorded unless the clinician explicitly accepts it. */
    public record ProposedUpdate(AssistantOutputContract.UpdateCategory category, String description, String rationale,
                                 String proposalReference) {
    }

    public record Evidence(String evidenceKey, UUID documentId, String documentTitle, String documentVersion,
                           String sourceOrganization, String section, Integer page, LocalDate publicationDate,
                           BigDecimal retrievalSimilarity, String excerpt, boolean syntheticSource) {
    }
}
