package com.medassist.common.domain;

/** Severity scale shared by rule results, safety alerts and AI red flags (ordered from lowest to highest). */
public enum ClinicalSeverity {
    INFO,
    WARNING,
    URGENT,
    CRITICAL
}
