package com.medassist.investigation.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Investigation workflow. Transitions are clinician actions only; there is no code path through which AI output can
 * move an investigation to ORDERED.
 */
public enum InvestigationStatus {
    SUGGESTED,
    ACCEPTED,
    REJECTED,
    ORDERED,
    COMPLETED;

    public Set<InvestigationStatus> allowedNext() {
        return switch (this) {
            case SUGGESTED -> EnumSet.of(ACCEPTED, REJECTED);
            case ACCEPTED -> EnumSet.of(ORDERED, REJECTED);
            case ORDERED -> EnumSet.of(COMPLETED);
            case REJECTED -> EnumSet.of(ACCEPTED);
            case COMPLETED -> EnumSet.noneOf(InvestigationStatus.class);
        };
    }
}
