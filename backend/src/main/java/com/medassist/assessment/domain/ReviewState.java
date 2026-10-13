package com.medassist.assessment.domain;

/** Human-in-the-loop state of an AI suggestion, derived from clinician feedback and finalization. */
public enum ReviewState {
    AI_GENERATED,
    REVIEWED,
    ACCEPTED,
    REJECTED,
    MODIFIED,
    FINALIZED;

    public static ReviewState from(FeedbackAction latestAction, boolean modified, boolean assessmentFinalized) {
        if (latestAction == null) {
            return AI_GENERATED;
        }
        ReviewState state = switch (latestAction) {
            case ACCEPTED -> modified ? MODIFIED : ACCEPTED;
            case REJECTED, NOT_RELEVANT -> REJECTED;
            case UNCERTAIN, NEEDS_MORE_INFORMATION -> REVIEWED;
        };
        return assessmentFinalized && (state == ACCEPTED || state == MODIFIED) ? FINALIZED : state;
    }
}
