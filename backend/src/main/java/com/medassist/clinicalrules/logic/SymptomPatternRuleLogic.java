package com.medassist.clinicalrules.logic;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleLogic;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContext.SymptomFact;
import com.medassist.symptom.domain.OnsetPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Matches a present symptom with optional required attributes (onset pattern, minimum severity) and optional
 * associated symptoms (at least one must be present).
 */
@Component
public class SymptomPatternRuleLogic implements RuleLogic {

    public static final String KEY = "symptom-pattern";

    public record Parameters(String symptom, OnsetPattern onsetPattern, Integer minSeverity,
                             List<String> associatedAnyOf) {
    }

    private final RuleParameters parameterReader;

    public SymptomPatternRuleLogic(RuleParameters parameterReader) {
        this.parameterReader = parameterReader;
    }

    @Override
    public String logicKey() {
        return KEY;
    }

    @Override
    public ClinicalRule bind(RuleMetadata metadata, JsonNode json) {
        Parameters p = parameterReader.read(json, Parameters.class);
        if (p.symptom() == null || p.symptom().isBlank()) {
            throw new IllegalArgumentException("symptom is required");
        }
        if (p.minSeverity() != null && (p.minSeverity() < 0 || p.minSeverity() > 10)) {
            throw new IllegalArgumentException("minSeverity must be 0-10");
        }
        return new Rule(metadata, p);
    }

    private record Rule(RuleMetadata metadata, Parameters p) implements ClinicalRule {

        @Override
        public RuleResult evaluate(ClinicalContext context) {
            Optional<SymptomFact> match = context.presentSymptom(p.symptom());
            if (match.isEmpty()) {
                return RuleResult.notTriggered(metadata, p.symptom() + " not recorded as present", List.of());
            }
            SymptomFact s = match.get();
            List<SupportingObservation> support = new ArrayList<>();
            support.add(new SupportingObservation(s.factKey(), "Symptom", s.symptomId(), s.displayName() + " present"));
            List<String> features = new ArrayList<>();
            features.add(s.displayName() + " present");
            if (p.onsetPattern() != null) {
                if (s.onsetPattern() != p.onsetPattern()) {
                    return RuleResult.notTriggered(metadata, "Onset pattern " + (s.onsetPattern() == null
                            ? "not recorded" : "is " + s.onsetPattern()), support);
                }
                features.add("onset " + s.onsetPattern().name().toLowerCase());
            }
            if (p.minSeverity() != null) {
                if (s.severity() == null || s.severity() < p.minSeverity()) {
                    return RuleResult.notTriggered(metadata, "Severity " + (s.severity() == null ? "not recorded"
                            : s.severity() + "/10 below rule minimum"), support);
                }
                features.add("severity " + s.severity() + "/10");
            }
            if (p.associatedAnyOf() != null && !p.associatedAnyOf().isEmpty()) {
                List<SymptomFact> associated = p.associatedAnyOf().stream()
                        .map(context::presentSymptom).flatMap(Optional::stream).toList();
                if (associated.isEmpty()) {
                    return RuleResult.notTriggered(metadata, "No required associated symptom recorded", support);
                }
                associated.forEach(a -> {
                    support.add(new SupportingObservation(a.factKey(), "Symptom", a.symptomId(), a.displayName() + " present"));
                    features.add(a.displayName() + " present");
                });
            }
            String qualifier = metadata.demonstrationOnly() ? " (demonstration rule)" : "";
            return RuleResult.triggered(metadata, String.join(", ", features) + qualifier, support);
        }
    }
}
