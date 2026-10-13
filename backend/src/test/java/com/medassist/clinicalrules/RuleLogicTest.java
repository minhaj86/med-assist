package com.medassist.clinicalrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleCategory;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.laboratory.CriticalLabFlagRuleLogic;
import com.medassist.clinicalrules.logic.RuleParameters;
import com.medassist.clinicalrules.logic.SymptomPatternRuleLogic;
import com.medassist.clinicalrules.logic.VitalSignThresholdRuleLogic;
import com.medassist.clinicalrules.neurological.ConsciousnessLevelRuleLogic;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.symptom.domain.OnsetPattern;
import com.medassist.support.ContextFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Tests rule logic with ILLUSTRATIVE thresholds (structure of behaviour, not clinical validity). */
class RuleLogicTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RuleParameters parameters = new RuleParameters(mapper);

    private static RuleMetadata meta(String ruleId) {
        return new RuleMetadata(UUID.randomUUID(), ruleId, "1.0.0-test", "Test rule", RuleCategory.GENERAL,
                ClinicalSeverity.URGENT, "x", "desc", "Review", List.of(), "test source", "nobody", true,
                Instant.EPOCH, null);
    }

    private ClinicalRule threshold(String json) {
        return new VitalSignThresholdRuleLogic(parameters).bind(meta("T-1"), mapper.readTree(json));
    }

    @Test
    void anyMatchTriggersOnSingleConditionAndLinksSupportingFact() {
        ClinicalRule rule = threshold("""
                {"match":"ANY","conditions":[{"vitalSign":"SYSTOLIC_BLOOD_PRESSURE","operator":"GE","threshold":180},
                 {"vitalSign":"DIASTOLIC_BLOOD_PRESSURE","operator":"GE","threshold":120}]}""");
        RuleResult result = rule.evaluate(ContextFixtures.context()
                .vital(VitalSignType.SYSTOLIC_BLOOD_PRESSURE, "185").vital(VitalSignType.DIASTOLIC_BLOOD_PRESSURE, "90")
                .build());
        assertThat(result.triggered()).isTrue();
        assertThat(result.explanation()).contains("185 mm[Hg] >= 180").contains("demonstration threshold");
        assertThat(result.supportingObservations()).singleElement()
                .satisfies(s -> assertThat(s.factKey()).isEqualTo("F1"));
        assertThat(result.demonstrationOnly()).isTrue();
    }

    @Test
    void allMatchRequiresEveryConditionAndReportsMissingData() {
        ClinicalRule rule = threshold("""
                {"match":"ALL","conditions":[{"vitalSign":"BODY_TEMPERATURE","operator":"GE","threshold":38.0},
                 {"vitalSign":"SYSTOLIC_BLOOD_PRESSURE","operator":"LT","threshold":100}]}""");
        RuleResult missing = rule.evaluate(ContextFixtures.context().vital(VitalSignType.BODY_TEMPERATURE, "38.5").build());
        assertThat(missing.triggered()).isFalse();
        assertThat(missing.explanation()).contains("not recorded: Systolic blood pressure");

        RuleResult both = rule.evaluate(ContextFixtures.context().vital(VitalSignType.BODY_TEMPERATURE, "38.5")
                .vital(VitalSignType.SYSTOLIC_BLOOD_PRESSURE, "92").build());
        assertThat(both.triggered()).isTrue();
        assertThat(both.supportingObservations()).hasSize(2);
    }

    @Test
    void boundaryOperatorsAreExact() {
        ClinicalRule lessThan = threshold("""
                {"match":"ANY","conditions":[{"vitalSign":"OXYGEN_SATURATION","operator":"LT","threshold":90}]}""");
        assertThat(lessThan.evaluate(ContextFixtures.context().vital(VitalSignType.OXYGEN_SATURATION, "90").build())
                .triggered()).isFalse();
        assertThat(lessThan.evaluate(ContextFixtures.context().vital(VitalSignType.OXYGEN_SATURATION, "89").build())
                .triggered()).isTrue();
    }

    @Test
    void invalidParametersAreRejectedAtBindTime() {
        assertThatThrownBy(() -> threshold("{\"match\":\"ANY\",\"conditions\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> threshold("{\"match\":\"SOMETIMES\",\"conditions\":[]}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> threshold("""
                {"match":"ANY","conditions":[{"vitalSign":"CONSCIOUSNESS_LEVEL","operator":"LT","threshold":1}]}"""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void symptomPatternRequiresAllConfiguredAttributes() {
        ClinicalRule rule = new SymptomPatternRuleLogic(parameters).bind(meta("S-1"),
                mapper.readTree("{\"symptom\":\"Headache\",\"onsetPattern\":\"SUDDEN\",\"minSeverity\":8}"));
        assertThat(rule.evaluate(ContextFixtures.context().symptom("Headache", true, OnsetPattern.SUDDEN, 9).build())
                .triggered()).isTrue();
        assertThat(rule.evaluate(ContextFixtures.context().symptom("Headache", true, OnsetPattern.GRADUAL, 9).build())
                .triggered()).isFalse();
        RuleResult noSeverity = rule.evaluate(ContextFixtures.context()
                .symptom("Headache", true, OnsetPattern.SUDDEN, null).build());
        assertThat(noSeverity.triggered()).isFalse();
        assertThat(noSeverity.explanation()).contains("not recorded");
        assertThat(rule.evaluate(ContextFixtures.context().symptom("Headache", false, OnsetPattern.SUDDEN, 10).build())
                .triggered()).isFalse();
    }

    @Test
    void consciousnessRuleUsesConfiguredLevelsAndConfusionSymptom() {
        ClinicalRule rule = new ConsciousnessLevelRuleLogic(parameters).bind(meta("N-1"), mapper.readTree(
                "{\"abnormalLevels\":[\"NEW_CONFUSION\",\"VOICE\",\"PAIN\",\"UNRESPONSIVE\"],\"includeConfusionSymptom\":true}"));
        assertThat(rule.evaluate(ContextFixtures.context().consciousness(ConsciousnessLevel.ALERT).build()).triggered())
                .isFalse();
        assertThat(rule.evaluate(ContextFixtures.context().consciousness(ConsciousnessLevel.VOICE).build()).triggered())
                .isTrue();
        assertThat(rule.evaluate(ContextFixtures.context().symptom("Confusion", true, null, null).build()).triggered())
                .isTrue();
        assertThatThrownBy(() -> new ConsciousnessLevelRuleLogic(parameters).bind(meta("N-2"),
                mapper.readTree("{\"abnormalLevels\":[\"ALERT\"]}"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void criticalLabRuleOnlyTrustsLaboratoryReportedFlags() {
        ClinicalRule rule = new CriticalLabFlagRuleLogic(parameters).bind(meta("L-1"),
                mapper.readTree("{\"flags\":[\"CRITICAL_LOW\",\"CRITICAL_HIGH\"]}"));
        assertThat(rule.evaluate(ContextFixtures.context()
                .lab("Lactate", "4.1", AbnormalFlag.CRITICAL_HIGH, AbnormalFlagSource.REPORTED).build()).triggered())
                .isTrue();
        assertThat(rule.evaluate(ContextFixtures.context()
                .lab("Lactate", "4.1", AbnormalFlag.HIGH, AbnormalFlagSource.DERIVED_FROM_REFERENCE_RANGE).build())
                .triggered()).isFalse();
    }
}
