package com.medassist.terminology.domain;

/**
 * How trustworthy a concept's code is. Codes are never invented: when no terminology source can confirm a
 * code, the concept carries a display name only and is marked {@link #UNVERIFIED}.
 */
public enum ConceptVerificationStatus {
    /** Confirmed by a configured terminology service. */
    VERIFIED_TERMINOLOGY_SERVICE,
    /** From the curated local catalog, with a documented published source. */
    CURATED_REFERENCE,
    /** Code entered by a clinician or received from an external system, not independently verified. */
    PROVIDED_UNVERIFIED,
    /** No code: free-text concept. */
    UNVERIFIED
}
