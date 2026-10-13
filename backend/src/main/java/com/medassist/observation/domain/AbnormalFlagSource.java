package com.medassist.observation.domain;

public enum AbnormalFlagSource {
    /** Flag provided by the laboratory / source system. */
    REPORTED,
    /** Derived deterministically by comparing the value to the reference range supplied with the result. */
    DERIVED_FROM_REFERENCE_RANGE,
    /** No flag available. */
    NONE
}
