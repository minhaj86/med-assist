package com.medassist.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.assessment.application.AssessmentChangeCalculator;
import com.medassist.assessment.application.AssessmentChanges;
import com.medassist.assessment.application.AssessmentChanges.ChangeType;
import com.medassist.assessment.application.ContextSnapshot;
import com.medassist.assessment.application.ContextSnapshot.SnapshotFact;
import com.medassist.common.domain.DataProvenance;
import com.medassist.diagnosis.context.FactCategory;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssessmentChangeCalculatorTest {

    private static SnapshotFact fact(String identity, FactCategory category, String description) {
        return new SnapshotFact("F1", category, description, "x", UUID.randomUUID(), identity, DataProvenance.CLINICIAN);
    }

    @Test
    void reportsAddedRemovedAndChangedFactsByStableIdentity() {
        ContextSnapshot before = new ContextSnapshot(56, "MALE", "EMERGENCY", List.of(
                fact("VITAL_SIGN:HEART_RATE", FactCategory.VITAL_SIGN, "Heart rate: 120 /min"),
                fact("SYMPTOM:1", FactCategory.SYMPTOM, "Cough: present")));
        ContextSnapshot after = new ContextSnapshot(56, "MALE", "EMERGENCY", List.of(
                fact("VITAL_SIGN:HEART_RATE", FactCategory.VITAL_SIGN, "Heart rate: 96 /min"),
                fact("LAB:troponin", FactCategory.LAB_RESULT, "Troponin: 5 ng/L")));
        Map<String, String> prevDiff = new LinkedHashMap<>();
        prevDiff.put("Condition A", "HIGH");
        prevDiff.put("Condition B", "LOW");
        Map<String, String> currDiff = new LinkedHashMap<>();
        currDiff.put("Condition A", "MODERATE");
        currDiff.put("Condition C", null);

        AssessmentChanges changes = new AssessmentChangeCalculator().compute(1, before, after,
                new AssessmentChangeCalculator.OutputSummary(List.of("[RULE] Alert 1"), prevDiff, AiReasoningStatus.COMPLETED),
                new AssessmentChangeCalculator.OutputSummary(List.of("[RULE] Alert 2"), currDiff, AiReasoningStatus.UNAVAILABLE));

        assertThat(changes.previousVersionNumber()).isEqualTo(1);
        assertThat(changes.inputChanges()).extracting(AssessmentChanges.Change::type)
                .containsExactlyInAnyOrder(ChangeType.CHANGED, ChangeType.ADDED, ChangeType.REMOVED);
        assertThat(changes.inputChanges()).filteredOn(c -> c.type() == ChangeType.CHANGED).singleElement()
                .satisfies(c -> assertThat(c.previousDescription()).isEqualTo("Heart rate: 120 /min"));
        assertThat(changes.outputChanges()).extracting(AssessmentChanges.Change::type)
                .containsExactlyInAnyOrder(ChangeType.NEW_ALERT, ChangeType.RESOLVED_ALERT, ChangeType.CONFIDENCE_CHANGED,
                        ChangeType.NEW_DIFFERENTIAL, ChangeType.REMOVED_DIFFERENTIAL, ChangeType.AI_STATUS_CHANGED);
    }
}
