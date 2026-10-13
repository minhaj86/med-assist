package com.medassist.diagnosis.reasoning;

import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.util.List;

/**
 * AI reasoning AFTER deterministic validation. Every statement carries its citation status; nothing in here has
 * bypassed schema, citation, terminology or safety validation.
 */
public record ValidatedReasoning(List<RedFlag> redFlags, List<Differential> differentials,
                                 List<String> uncertainties, List<String> additionalQuestions) {

    public record Statement(String text, List<String> factKeys, List<String> evidenceKeys, SupportStatus support) {
    }

    public record RedFlag(String title, ClinicalSeverity severity, String reason, List<String> factKeys,
                          List<String> evidenceKeys, SupportStatus support) {
    }

    public record Investigation(String name, String reason, List<String> evidenceKeys, SupportStatus support) {
    }

    public record Differential(String conditionName, CodingSystem codingSystem, String code,
                               ConceptVerificationStatus codeVerification, ConfidenceLevel confidence,
                               ConfidenceLevel originalConfidence, String whyConsidered,
                               List<Statement> supportingEvidence, List<Statement> contradictingEvidence,
                               List<String> missingInformation, List<Investigation> suggestedInvestigations,
                               String distinguishingFeatures, String reasoningSummary, List<String> citations,
                               GroundingStatus grounding, List<String> validationNotes) {
    }
}
