package com.medassist.assessment.application;

import com.medassist.assessment.application.AssessmentChanges.Change;
import com.medassist.assessment.application.AssessmentChanges.ChangeType;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Computes the clinician-readable difference between two assessment versions. */
@Component
public class AssessmentChangeCalculator {

    /** Output summary of a version used for comparison. */
    public record OutputSummary(List<String> alertTitles, Map<String, String> differentialConfidence,
                                AiReasoningStatus aiStatus) {
    }

    public AssessmentChanges compute(int previousVersionNumber, ContextSnapshot previous, ContextSnapshot current,
                                     OutputSummary previousOutput, OutputSummary currentOutput) {
        return new AssessmentChanges(previousVersionNumber, inputChanges(previous, current),
                outputChanges(previousOutput, currentOutput));
    }

    List<Change> inputChanges(ContextSnapshot previous, ContextSnapshot current) {
        Map<String, ContextSnapshot.SnapshotFact> before = index(previous.facts());
        Map<String, ContextSnapshot.SnapshotFact> after = index(current.facts());
        List<Change> changes = new ArrayList<>();
        after.forEach((identity, fact) -> {
            ContextSnapshot.SnapshotFact old = before.get(identity);
            if (old == null) {
                changes.add(new Change(ChangeType.ADDED, fact.category().name(), fact.description(), null));
            } else if (!Objects.equals(old.description(), fact.description())) {
                changes.add(new Change(ChangeType.CHANGED, fact.category().name(), fact.description(), old.description()));
            }
        });
        before.forEach((identity, fact) -> {
            if (!after.containsKey(identity)) {
                changes.add(new Change(ChangeType.REMOVED, fact.category().name(), fact.description(), null));
            }
        });
        return List.copyOf(changes);
    }

    List<Change> outputChanges(OutputSummary previous, OutputSummary current) {
        List<Change> changes = new ArrayList<>();
        for (String title : current.alertTitles()) {
            if (!previous.alertTitles().contains(title)) {
                changes.add(new Change(ChangeType.NEW_ALERT, "SAFETY_ALERT", title, null));
            }
        }
        for (String title : previous.alertTitles()) {
            if (!current.alertTitles().contains(title)) {
                changes.add(new Change(ChangeType.RESOLVED_ALERT, "SAFETY_ALERT", title, null));
            }
        }
        Map<String, String> prevDiff = lowerKeys(previous.differentialConfidence());
        Map<String, String> currDiff = lowerKeys(current.differentialConfidence());
        current.differentialConfidence().forEach((name, confidence) -> {
            String key = name.toLowerCase(Locale.ROOT);
            if (!prevDiff.containsKey(key)) {
                changes.add(new Change(ChangeType.NEW_DIFFERENTIAL, "DIFFERENTIAL", describe(name, confidence), null));
            } else if (!Objects.equals(prevDiff.get(key), confidence)) {
                changes.add(new Change(ChangeType.CONFIDENCE_CHANGED, "DIFFERENTIAL", describe(name, confidence),
                        describe(name, prevDiff.get(key))));
            }
        });
        previous.differentialConfidence().forEach((name, confidence) -> {
            if (!currDiff.containsKey(name.toLowerCase(Locale.ROOT))) {
                changes.add(new Change(ChangeType.REMOVED_DIFFERENTIAL, "DIFFERENTIAL", name, null));
            }
        });
        if (previous.aiStatus() != current.aiStatus()) {
            changes.add(new Change(ChangeType.AI_STATUS_CHANGED, "AI", String.valueOf(current.aiStatus()),
                    String.valueOf(previous.aiStatus())));
        }
        return List.copyOf(changes);
    }

    private static String describe(String name, String confidence) {
        return confidence == null ? name + " (not AI-assessed)" : name + " (confidence " + confidence + ")";
    }

    private static Map<String, ContextSnapshot.SnapshotFact> index(List<ContextSnapshot.SnapshotFact> facts) {
        return facts.stream().collect(Collectors.toMap(ContextSnapshot.SnapshotFact::identity, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
    }

    private static Map<String, String> lowerKeys(Map<String, String> map) {
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
        return result;
    }
}
