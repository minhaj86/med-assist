package com.medassist.terminology.domain;

import java.util.List;

/** Port for terminology operations. Implementations must never fabricate codes. */
public interface TerminologyService {

    /** Normalizes free text to a known concept (canonical display name, code only if verifiable). */
    ClinicalConcept normalize(String text, ConceptDomain domain);

    /** Concept suggestions for type-ahead entry. */
    List<ClinicalConcept> suggest(String query, ConceptDomain domain, int limit);

    /**
     * Checks a code supplied by an external party (AI model, clinician, FHIR import). Returns a concept whose
     * verification status reflects the outcome; unverifiable codes are returned with status
     * {@link ConceptVerificationStatus#PROVIDED_UNVERIFIED} so callers can decide whether to drop them.
     */
    ClinicalConcept verify(CodingSystem system, String code, String displayName);

    /** Human-readable description of the active terminology sources (for admin status pages). */
    String describeSources();
}
