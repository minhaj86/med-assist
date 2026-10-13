package com.medassist.diagnosis.differential;

/**
 * Qualitative confidence that a possibility deserves consideration. Deliberately not a probability: no validated
 * statistical model backs these values.
 */
public enum ConfidenceLevel {
    LOW,
    MODERATE,
    HIGH
}
