package com.medassist.diagnosis.differential;

public enum SupportStatus {
    /** Every cited evidence id exists in the provided clinical facts / retrieved knowledge. */
    SUPPORTED,
    /** No valid citation - shown to clinicians as "unsupported". */
    UNSUPPORTED,
    /** Removed by the safety validator (e.g. definitive diagnostic language or fabricated references). */
    WITHHELD,
    /** Citations are not applicable (e.g. a missing-information item). */
    NOT_APPLICABLE
}
