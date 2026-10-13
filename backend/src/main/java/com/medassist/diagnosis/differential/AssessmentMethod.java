package com.medassist.diagnosis.differential;

public enum AssessmentMethod {
    /** Proposed and explained by the AI reasoning step, then validated deterministically. */
    AI_REASONED,
    /** Condition discussed in retrieved knowledge for these findings; not assessed by AI. */
    KNOWLEDGE_LINKED
}
