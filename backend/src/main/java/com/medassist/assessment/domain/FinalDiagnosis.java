package com.medassist.assessment.domain;

import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.util.UUID;

/**
 * A diagnosis documented by the clinician in the final assessment. Only clinician-entered or clinician-accepted
 * entries exist here; AI suggestions never become final diagnoses on their own.
 */
public record FinalDiagnosis(String conditionName, CodingSystem codingSystem, String code,
                             ConceptVerificationStatus codeVerification, DiagnosisCertainty certainty,
                             UUID sourceSuggestionId) {

    public enum DiagnosisCertainty {
        PROVISIONAL,
        DIFFERENTIAL,
        CONFIRMED
    }
}
