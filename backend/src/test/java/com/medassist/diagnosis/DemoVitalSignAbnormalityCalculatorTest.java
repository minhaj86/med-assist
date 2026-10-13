package com.medassist.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.scoring.DemoVitalSignAbnormalityCalculator;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.observation.domain.ConsciousnessLevel;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.support.ContextFixtures;
import org.junit.jupiter.api.Test;

class DemoVitalSignAbnormalityCalculatorTest {

    private final DemoVitalSignAbnormalityCalculator calculator = new DemoVitalSignAbnormalityCalculator();

    @Test
    void doesNotCalculateWithTooFewInputsAndListsMissingVariables() {
        ScoreResult result = calculator.calculate(ContextFixtures.context().vital(VitalSignType.HEART_RATE, "120").build());
        assertThat(result.score()).isNull();
        assertThat(result.missingVariables()).hasSize(5);
        assertThat(result.interpretation()).startsWith("Insufficient information");
        assertThat(result.demonstrationOnly()).isTrue();
    }

    @Test
    void countsOutOfRangeValuesTransparently() {
        ScoreResult result = calculator.calculate(ContextFixtures.context()
                .vital(VitalSignType.HEART_RATE, "120").vital(VitalSignType.RESPIRATORY_RATE, "26")
                .vital(VitalSignType.OXYGEN_SATURATION, "91").vital(VitalSignType.BODY_TEMPERATURE, "37.0")
                .consciousness(ConsciousnessLevel.ALERT).build());
        assertThat(result.score()).isEqualTo(3);
        assertThat(result.calculationDetails()).hasSize(5);
        assertThat(result.inputVariables()).containsKey("Heart rate");
        assertThat(result.missingVariables()).containsExactly("Systolic blood pressure");
        assertThat(result.alertSeverity()).isEqualTo(ClinicalSeverity.WARNING);
        assertThat(result.interpretation()).contains("not a validated");
        assertThat(result.reference()).contains("Not derived from a published clinical score");
    }
}
