package com.medassist.assessment.domain;

/** Pipeline stages, reported to the UI as they happen (no synthetic progress percentages). */
public enum AssessmentStage {
    QUEUED,
    VALIDATING_INPUT,
    NORMALIZING_TERMINOLOGY,
    RUNNING_RULES,
    CALCULATING_SCORES,
    SEARCHING_KNOWLEDGE,
    GENERATING_CANDIDATES,
    GENERATING_REASONING,
    VALIDATING_RESPONSE,
    SAVING,
    COMPLETED,
    FAILED
}
