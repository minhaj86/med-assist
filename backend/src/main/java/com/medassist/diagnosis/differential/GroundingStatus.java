package com.medassist.diagnosis.differential;

public enum GroundingStatus {
    /** At least one valid knowledge citation and all statements supported. */
    GROUNDED,
    /** Valid knowledge citations exist but some statements are unsupported. */
    PARTIALLY_GROUNDED,
    /** No valid knowledge citation. */
    UNGROUNDED
}
