package com.medassist.terminology.domain;

/** Supported code systems and their canonical (FHIR) system URIs. */
public enum CodingSystem {
    SNOMED_CT("http://snomed.info/sct"),
    ICD_10("http://hl7.org/fhir/sid/icd-10"),
    LOINC("http://loinc.org"),
    LOCAL("urn:medassist:local");

    private final String uri;

    CodingSystem(String uri) {
        this.uri = uri;
    }

    public String uri() {
        return uri;
    }

    public static CodingSystem fromUri(String uri) {
        for (CodingSystem system : values()) {
            if (system.uri.equals(uri)) {
                return system;
            }
        }
        return null;
    }
}
