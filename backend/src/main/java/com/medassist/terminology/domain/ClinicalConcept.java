package com.medassist.terminology.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * A reference to a clinical concept. {@code code} is null unless a terminology source supplied it; the
 * verification status always states how far the code can be trusted.
 */
@Embeddable
public record ClinicalConcept(
        @Enumerated(EnumType.STRING) @Column(name = "concept_system", length = 30) CodingSystem system,
        @Column(name = "concept_code", length = 50) String code,
        @Column(name = "concept_display", length = 200) String displayName,
        @Enumerated(EnumType.STRING) @Column(name = "concept_verification", length = 40)
        ConceptVerificationStatus verificationStatus) {

    public ClinicalConcept {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(verificationStatus, "verificationStatus");
        if (code != null && system == null) {
            throw new IllegalArgumentException("A coded concept requires a code system");
        }
        if (code == null && verificationStatus != ConceptVerificationStatus.UNVERIFIED) {
            throw new IllegalArgumentException("A concept without a code must be UNVERIFIED");
        }
    }

    public static ClinicalConcept unverified(String displayName) {
        return new ClinicalConcept(null, null, displayName, ConceptVerificationStatus.UNVERIFIED);
    }

    public boolean isCoded() {
        return code != null;
    }
}
