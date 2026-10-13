package com.medassist.common.domain;

/**
 * Origin of a clinical value. AI-generated information must never silently become confirmed clinical data:
 * {@link #AI_INFERRED} records can only exist when a clinician has explicitly confirmed them.
 */
public enum DataProvenance {
    CLINICIAN,
    FHIR,
    LAB_IMPORT,
    PATIENT_REPORTED,
    DEVICE,
    AI_INFERRED
}
