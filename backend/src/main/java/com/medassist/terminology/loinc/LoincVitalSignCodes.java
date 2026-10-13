package com.medassist.terminology.loinc;

/**
 * LOINC codes for vital signs as published in the HL7 FHIR R4 Vital Signs profile
 * (http://hl7.org/fhir/R4/observation-vitalsigns.html). These are the only codes shipped with MedAssist;
 * all other concepts remain uncoded unless a terminology service verifies them.
 */
public final class LoincVitalSignCodes {

    public static final String SOURCE = "HL7 FHIR R4 Vital Signs profile (observation-vitalsigns)";

    public static final String SYSTOLIC_BLOOD_PRESSURE = "8480-6";
    public static final String DIASTOLIC_BLOOD_PRESSURE = "8462-4";
    public static final String HEART_RATE = "8867-4";
    public static final String RESPIRATORY_RATE = "9279-1";
    public static final String OXYGEN_SATURATION_PULSE_OXIMETRY = "59408-5";
    public static final String BODY_TEMPERATURE = "8310-5";
    public static final String BODY_WEIGHT = "29463-7";
    public static final String BODY_HEIGHT = "8302-2";
    public static final String BODY_MASS_INDEX = "39156-5";

    private LoincVitalSignCodes() {
    }
}
