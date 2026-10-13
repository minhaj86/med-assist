package com.medassist.clinicalrules.laboratory;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleLogic;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.clinicalrules.logic.RuleParameters;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContext.LabFact;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Surfaces laboratory results that the reporting laboratory itself flagged as critical. No thresholds are applied
 * by MedAssist - the flag must come from the source system.
 */
@Component
public class CriticalLabFlagRuleLogic implements RuleLogic {

    public static final String KEY = "critical-lab-flag";

    public record Parameters(Set<AbnormalFlag> flags) {
    }

    private final RuleParameters parameterReader;

    public CriticalLabFlagRuleLogic(RuleParameters parameterReader) {
        this.parameterReader = parameterReader;
    }

    @Override
    public String logicKey() {
        return KEY;
    }

    @Override
    public ClinicalRule bind(RuleMetadata metadata, JsonNode json) {
        Parameters p = parameterReader.read(json, Parameters.class);
        if (p.flags() == null || p.flags().isEmpty()) {
            throw new IllegalArgumentException("flags must not be empty");
        }
        return new Rule(metadata, p);
    }

    private record Rule(RuleMetadata metadata, Parameters p) implements ClinicalRule {

        @Override
        public RuleResult evaluate(ClinicalContext context) {
            List<LabFact> critical = context.labs().stream()
                    .filter(l -> l.abnormalFlag() != null && p.flags().contains(l.abnormalFlag())
                            && l.flagSource() == AbnormalFlagSource.REPORTED)
                    .toList();
            if (critical.isEmpty()) {
                return RuleResult.notTriggered(metadata, "No laboratory-reported critical values", List.of());
            }
            List<SupportingObservation> support = critical.stream()
                    .map(l -> new SupportingObservation(l.factKey(), "LabObservation", l.labId(),
                            l.testName() + " flagged " + l.abnormalFlag() + " by the reporting laboratory"))
                    .toList();
            String explanation = String.join("; ", support.stream().map(SupportingObservation::description).toList());
            return RuleResult.triggered(metadata, explanation, support);
        }
    }
}
