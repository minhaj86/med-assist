package com.medassist.diagnosis.reasoning;

public enum AiReasoningStatus {
    /** AI output received and passed structural, citation and safety validation. */
    COMPLETED,
    /** Clinician ran the assessment without AI reasoning. */
    NOT_REQUESTED,
    /** No AI provider is configured for this installation. */
    NOT_CONFIGURED,
    /** Provider unavailable (timeout, error, circuit open). Deterministic results remain valid. */
    UNAVAILABLE,
    /** Provider responded but the output failed validation; nothing from it is shown. */
    OUTPUT_REJECTED
}
