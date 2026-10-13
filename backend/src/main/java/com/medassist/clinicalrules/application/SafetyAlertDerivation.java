package com.medassist.clinicalrules.application;

import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.common.domain.AlertOrigin;
import com.medassist.diagnosis.scoring.ScoreComponent;
import com.medassist.diagnosis.scoring.ScoreResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns triggered rules and score alerts into safety alerts, ordered by severity (highest first). */
@Component
public class SafetyAlertDerivation {

    public static final Comparator<DerivedSafetyAlert> ORDER = Comparator
            .comparing(DerivedSafetyAlert::severity).reversed()
            .thenComparing(DerivedSafetyAlert::origin)
            .thenComparing(DerivedSafetyAlert::title);

    public List<DerivedSafetyAlert> derive(RuleEvaluationReport report, List<ScoreResult> scores) {
        List<DerivedSafetyAlert> alerts = new ArrayList<>();
        for (RuleResult r : report.triggered()) {
            alerts.add(new DerivedSafetyAlert(AlertOrigin.RULE, r.severity(),
                    r.evaluationError() ? "Rule not evaluated: " + r.ruleName() : r.ruleName(), r.explanation(),
                    r.recommendedAction(), r.ruleId() + " v" + r.ruleVersion(),
                    r.supportingObservations().stream().map(SupportingObservation::factKey).toList(),
                    r.demonstrationOnly()));
        }
        for (ScoreResult s : scores) {
            if (s.alertSeverity() != null) {
                alerts.add(new DerivedSafetyAlert(AlertOrigin.CLINICAL_SCORE, s.alertSeverity(), s.alertMessage(),
                        s.interpretation(), "Review the score inputs; clinical evaluation is required.",
                        s.scoreId() + " v" + s.version(),
                        s.calculationDetails().stream().filter(c -> c.points() > 0).map(ScoreComponent::factKey).toList(),
                        s.demonstrationOnly()));
            }
        }
        alerts.sort(ORDER);
        return List.copyOf(alerts);
    }
}
