package com.medassist.fhir.mapping;

/**
 * FHIR system URIs and code values from the HL7 FHIR R4 specification / HL7 terminology. MedAssist-specific
 * identifiers use the {@code urn:medassist:} namespace so they cannot be mistaken for standard ones.
 */
public final class FhirCodes {

    public static final String PATIENT_IDENTIFIER_SYSTEM = "urn:medassist:external-patient-id";
    public static final String BIOLOGICAL_SEX_EXTENSION = "urn:medassist:fhir:extension:biological-sex";
    public static final String MEDASSIST_TAG_SYSTEM = "urn:medassist:tag";
    public static final String SYNTHETIC_TAG = "synthetic-test-data";

    public static final String ACT_CODE_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ActCode";
    public static final String OBSERVATION_CATEGORY_SYSTEM = "http://terminology.hl7.org/CodeSystem/observation-category";
    public static final String INTERPRETATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";
    public static final String CONDITION_CLINICAL_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-clinical";
    public static final String CONDITION_VERIFICATION_SYSTEM = "http://terminology.hl7.org/CodeSystem/condition-ver-status";
    public static final String ALLERGY_CLINICAL_SYSTEM = "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical";
    public static final String UCUM_SYSTEM = "http://unitsofmeasure.org";

    private FhirCodes() {
    }
}
