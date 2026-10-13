package com.medassist.clinicalrules.neurological;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleLogic;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.clinicalrules.logic.RuleParameters;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Flags a recorded level of consciousness in the configured abnormal set, optionally also a "Confusion" symptom. */
@Component
public class ConsciousnessLevelRuleLogic implements RuleLogic {

    public static final String KEY = "consciousness-level";

    public record Parameters(Set<ConsciousnessLevel> abnormalLevels, boolean includeConfusionSymptom) {
    }

    private final RuleParameters parameterReader;

    public ConsciousnessLevelRuleLogic(RuleParameters parameterReader) {
        this.parameterReader = parameterReader;
    }

    @Override
    public String logicKey() {
        return KEY;
    }

    @Override
    public ClinicalRule bind(RuleMetadata metadata, JsonNode json) {
        Parameters p = parameterReader.read(json, Parameters.class);
        if (p.abnormalLevels() == null || p.abnormalLevels().isEmpty()
                || p.abnormalLevels().contains(ConsciousnessLevel.ALERT)) {
            throw new IllegalArgumentException("abnormalLevels must be non-empty and must not include ALERT");
        }
        return new Rule(metadata, p);
    }

    private record Rule(RuleMetadata metadata, Parameters p) implements ClinicalRule {

        @Override
        public RuleResult evaluate(ClinicalContext context) {
            List<SupportingObservation> support = new ArrayList<>();
            List<String> findings = new ArrayList<>();
            context.latestVital(VitalSignType.CONSCIOUSNESS_LEVEL).ifPresent(v -> {
                if (v.consciousnessLevel() != null && p.abnormalLevels().contains(v.consciousnessLevel())) {
                    support.add(new SupportingObservation(v.factKey(), "Observation", v.observationId(),
                            "Level of consciousness: " + v.displayValue()));
                    findings.add("Level of consciousness recorded as '" + v.displayValue() + "'");
                }
            });
            if (p.includeConfusionSymptom()) {
                context.presentSymptom("Confusion").ifPresent(s -> {
                    support.add(new SupportingObservation(s.factKey(), "Symptom", s.symptomId(), "Confusion present"));
                    findings.add("Confusion recorded as present");
                });
            }
            if (findings.isEmpty()) {
                return RuleResult.notTriggered(metadata, "No reduced level of consciousness recorded", List.of());
            }
            return RuleResult.triggered(metadata, String.join("; ", findings), support);
        }
    }
}
