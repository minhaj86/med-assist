package com.medassist.clinicalrules.logic;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleLogic;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContext.VitalSignFact;
import com.medassist.observation.domain.VitalSignType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Compares the most recent value of one or more vital signs with configured thresholds. Thresholds come from the
 * versioned rule definition, never from code.
 */
@Component
public class VitalSignThresholdRuleLogic implements RuleLogic {

    public static final String KEY = "vital-sign-threshold";

    public enum Operator {
        LT("<"), LE("<="), GT(">"), GE(">=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        boolean test(BigDecimal value, BigDecimal threshold) {
            int cmp = value.compareTo(threshold);
            return switch (this) {
                case LT -> cmp < 0;
                case LE -> cmp <= 0;
                case GT -> cmp > 0;
                case GE -> cmp >= 0;
            };
        }
    }

    public enum Match {
        ANY, ALL
    }

    public record Condition(VitalSignType vitalSign, Operator operator, BigDecimal threshold) {
    }

    public record Parameters(List<Condition> conditions, Match match) {
    }

    private final RuleParameters parameterReader;

    public VitalSignThresholdRuleLogic(RuleParameters parameterReader) {
        this.parameterReader = parameterReader;
    }

    @Override
    public String logicKey() {
        return KEY;
    }

    @Override
    public ClinicalRule bind(RuleMetadata metadata, JsonNode json) {
        Parameters p = parameterReader.read(json, Parameters.class);
        if (p.conditions() == null || p.conditions().isEmpty() || p.match() == null) {
            throw new IllegalArgumentException("conditions and match are required");
        }
        for (Condition c : p.conditions()) {
            if (c.vitalSign() == null || c.operator() == null || c.threshold() == null
                    || c.vitalSign() == VitalSignType.CONSCIOUSNESS_LEVEL) {
                throw new IllegalArgumentException("Each condition needs a numeric vitalSign, operator and threshold");
            }
        }
        return new Rule(metadata, p);
    }

    private record Rule(RuleMetadata metadata, Parameters parameters) implements ClinicalRule {

        @Override
        public RuleResult evaluate(ClinicalContext context) {
            List<SupportingObservation> met = new ArrayList<>();
            List<SupportingObservation> examined = new ArrayList<>();
            List<String> metDescriptions = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            String qualifier = metadata.demonstrationOnly() ? " (demonstration threshold)" : "";
            for (Condition c : parameters.conditions()) {
                Optional<VitalSignFact> fact = context.latestVital(c.vitalSign());
                if (fact.isEmpty()) {
                    missing.add(c.vitalSign().displayName());
                    continue;
                }
                VitalSignFact v = fact.get();
                SupportingObservation observation = new SupportingObservation(v.factKey(), "Observation",
                        v.observationId(), c.vitalSign().displayName() + " " + v.displayValue());
                examined.add(observation);
                if (c.operator().test(v.value(), c.threshold())) {
                    met.add(observation);
                    metDescriptions.add(c.vitalSign().displayName() + " " + v.displayValue() + " "
                            + c.operator().symbol + " " + c.threshold().stripTrailingZeros().toPlainString() + " "
                            + v.unit() + qualifier);
                }
            }
            boolean triggered = parameters.match() == Match.ANY
                    ? !met.isEmpty()
                    : missing.isEmpty() && met.size() == parameters.conditions().size();
            if (triggered) {
                return RuleResult.triggered(metadata, String.join("; ", metDescriptions), met);
            }
            String reason = missing.isEmpty() ? "Recorded values do not meet the rule conditions"
                    : "Not fully evaluable - not recorded: " + String.join(", ", missing);
            return RuleResult.notTriggered(metadata, reason, examined);
        }
    }
}
