package com.medassist.assessment.application;

import java.util.List;

/** What changed between an assessment version and its predecessor (inputs and outputs). */
public record AssessmentChanges(Integer previousVersionNumber, List<Change> inputChanges, List<Change> outputChanges) {

    public enum ChangeType {
        ADDED, REMOVED, CHANGED, NEW_ALERT, RESOLVED_ALERT, NEW_DIFFERENTIAL, REMOVED_DIFFERENTIAL,
        CONFIDENCE_CHANGED, AI_STATUS_CHANGED
    }

    public record Change(ChangeType type, String category, String description, String previousDescription) {
    }

    public static AssessmentChanges initial() {
        return new AssessmentChanges(null, List.of(), List.of());
    }
}
