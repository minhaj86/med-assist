package com.medassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.ai.guardrail.SafetyGuardrail;
import com.medassist.ai.guardrail.SafetyGuardrail.FindingType;
import org.junit.jupiter.api.Test;

class SafetyGuardrailTest {

    private final SafetyGuardrail guardrail = new SafetyGuardrail();

    @Test
    void flagsDefinitiveDiagnosticLanguage() {
        assertThat(guardrail.inspect("The patient definitely has a myocardial infarction")).extracting(SafetyGuardrail.Finding::type)
                .contains(FindingType.DEFINITIVE_DIAGNOSIS_LANGUAGE);
        assertThat(guardrail.inspect("Pulmonary embolism has been ruled out")).isNotEmpty();
        assertThat(guardrail.inspect("The diagnosis is pneumonia")).isNotEmpty();
    }

    @Test
    void flagsFabricatedReferencesAndNumericProbabilities() {
        assertThat(guardrail.inspect("See https://example.org/guide")).extracting(SafetyGuardrail.Finding::type)
                .contains(FindingType.FABRICATED_REFERENCE);
        assertThat(guardrail.inspect("Smith et al. reported this")).isNotEmpty();
        assertThat(guardrail.inspect("As shown by Jones (2019)")).isNotEmpty();
        assertThat(guardrail.inspect("According to the ESC guidance this is common")).isNotEmpty();
        assertThat(guardrail.inspect("There is an 80% probability of PE")).extracting(SafetyGuardrail.Finding::type)
                .contains(FindingType.NUMERIC_PROBABILITY);
    }

    @Test
    void allowsHedgedDecisionSupportLanguage() {
        assertThat(guardrail.inspect("Evidence supports consideration of acute coronary syndrome; clinical evaluation is required.")).isEmpty();
        assertThat(guardrail.inspect("A D-dimer may help to rule out pulmonary embolism in low-probability patients.")).isEmpty();
        assertThat(guardrail.inspect("SpO2 93 % and heart rate 128 /min")).isEmpty();
        assertThat(guardrail.inspect("Insufficient information to reliably assess this possibility.")).isEmpty();
    }
}
