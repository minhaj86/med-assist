package com.medassist.common.domain;

/** Where a safety alert came from. Deterministic origins must never be presented as AI opinions (and vice versa). */
public enum AlertOrigin {
    RULE,
    CLINICAL_SCORE,
    AI,
    KNOWLEDGE_BASE,
    CLINICIAN
}
