package com.medassist.assessment.application;

import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.ProposedBy;
import com.medassist.assessment.domain.SuggestionType;
import java.util.List;
import java.util.UUID;

/** Domain event published when a clinician gives feedback on a suggestion (consumed by other modules). */
public record SuggestionFeedbackRecorded(UUID assessmentVersionId, UUID encounterId, UUID patientId,
                                         SuggestionType suggestionType, UUID suggestionId, String suggestionName,
                                         String suggestionReason, List<String> evidenceKeys, ProposedBy proposedBy,
                                         FeedbackAction action, String clinicianId, String feedbackReason) {
}
