package com.medassist.diagnosis.reasoning;

/**
 * Port through which the assessment pipeline obtains AI-assisted reasoning. Implementations must validate all
 * model output before returning it and must never throw for provider failures - they report a status instead, so
 * that deterministic assessment results are always delivered.
 */
public interface ClinicalReasoningPort {

    ReasoningOutcome reason(ReasoningInput input);
}
