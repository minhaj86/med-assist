package com.medassist.diagnosis.scoring;

import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContext.VitalSignFact;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * DEMONSTRATION calculator - NOT a validated clinical score and NOT NEWS2.
 *
 * <p>Counts how many of six recorded vital signs fall outside ILLUSTRATIVE ranges that exist only to demonstrate
 * the scoring framework (transparency, missing-variable handling, versioning). The ranges are not derived from
 * any published guideline and must not be used for patient care.
 */
@Component
public class DemoVitalSignAbnormalityCalculator implements ClinicalScoreCalculator {

    public static final String SCORE_ID = "DEMO-VITALS-OUT-OF-RANGE";

    /** ILLUSTRATIVE ranges for software demonstration only. */
    private record IllustrativeRange(VitalSignType type, BigDecimal low, BigDecimal high) {
    }

    private static final List<IllustrativeRange> ILLUSTRATIVE_RANGES = List.of(
            new IllustrativeRange(VitalSignType.RESPIRATORY_RATE, new BigDecimal("12"), new BigDecimal("20")),
            new IllustrativeRange(VitalSignType.OXYGEN_SATURATION, new BigDecimal("95"), new BigDecimal("100")),
            new IllustrativeRange(VitalSignType.SYSTOLIC_BLOOD_PRESSURE, new BigDecimal("100"), new BigDecimal("160")),
            new IllustrativeRange(VitalSignType.HEART_RATE, new BigDecimal("50"), new BigDecimal("100")),
            new IllustrativeRange(VitalSignType.BODY_TEMPERATURE, new BigDecimal("36.0"), new BigDecimal("38.0")));

    /** Illustrative demonstration alert threshold. */
    private static final int DEMO_ALERT_THRESHOLD = 3;

    @Override
    public String scoreId() {
        return SCORE_ID;
    }

    @Override
    public String name() {
        return "Demonstration vital-sign abnormality count (not a validated score)";
    }

    @Override
    public String version() {
        return "0.1.0-demo";
    }

    @Override
    public ScoreResult calculate(ClinicalContext context) {
        List<ScoreComponent> components = new ArrayList<>();
        Map<String, String> inputs = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        int score = 0;
        for (IllustrativeRange range : ILLUSTRATIVE_RANGES) {
            Optional<VitalSignFact> fact = context.latestVital(range.type());
            if (fact.isEmpty()) {
                missing.add(range.type().displayName());
                continue;
            }
            VitalSignFact v = fact.get();
            inputs.put(range.type().displayName(), v.displayValue());
            boolean outside = v.value().compareTo(range.low()) < 0 || v.value().compareTo(range.high()) > 0;
            int points = outside ? 1 : 0;
            score += points;
            components.add(new ScoreComponent(range.type().displayName(), v.displayValue(), points,
                    (outside ? "Outside" : "Within") + " illustrative range " + range.low().toPlainString() + "-"
                            + range.high().toPlainString() + " " + v.unit(), v.factKey()));
        }
        Optional<VitalSignFact> consciousness = context.latestVital(VitalSignType.CONSCIOUSNESS_LEVEL);
        if (consciousness.isEmpty()) {
            missing.add(VitalSignType.CONSCIOUSNESS_LEVEL.displayName());
        } else {
            VitalSignFact v = consciousness.get();
            boolean abnormal = v.consciousnessLevel() != ConsciousnessLevel.ALERT;
            inputs.put(VitalSignType.CONSCIOUSNESS_LEVEL.displayName(), v.displayValue());
            score += abnormal ? 1 : 0;
            components.add(new ScoreComponent(VitalSignType.CONSCIOUSNESS_LEVEL.displayName(), v.displayValue(),
                    abnormal ? 1 : 0, abnormal ? "Not 'Alert'" : "Alert", v.factKey()));
        }
        int recorded = components.size();
        if (recorded < 3) {
            return new ScoreResult(SCORE_ID, name(), version(), null, 6, components, inputs, missing,
                    "Insufficient information: only " + recorded + " of 6 vital signs recorded. Demonstration score "
                            + "not calculated.", reference(), true, null, null);
        }
        String interpretation = "Demonstration only: " + score + " of " + recorded
                + " recorded vital signs are outside illustrative ranges"
                + (missing.isEmpty() ? "." : " (" + missing.size() + " not recorded).")
                + " This is not a validated early-warning score.";
        boolean alert = score >= DEMO_ALERT_THRESHOLD;
        return new ScoreResult(SCORE_ID, name(), version(), score, 6, components, inputs, missing, interpretation,
                reference(), true, alert ? ClinicalSeverity.WARNING : null,
                alert ? "Several vital signs outside illustrative ranges (demonstration score " + score + ")" : null);
    }

    private static String reference() {
        return "MedAssist demonstration calculator. Not derived from a published clinical score; illustrative ranges "
                + "only. Do not use for patient care.";
    }
}
