package com.medassist.clinicalrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.medassist.clinicalrules.domain.RuleCategory;
import com.medassist.clinicalrules.domain.RuleDefinition;
import com.medassist.clinicalrules.domain.RuleDefinitionRepository;
import com.medassist.clinicalrules.engine.ClinicalRuleEngine;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.clinicalrules.logic.RuleParameters;
import com.medassist.clinicalrules.logic.VitalSignThresholdRuleLogic;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.common.json.JsonCodec;
import com.medassist.observation.domain.VitalSignType;
import com.medassist.support.ContextFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ClinicalRuleEngineTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RuleDefinitionRepository repository = mock(RuleDefinitionRepository.class);
    private final ClinicalRuleEngine engine = new ClinicalRuleEngine(repository,
            List.of(new VitalSignThresholdRuleLogic(new RuleParameters(mapper))), new JsonCodec(mapper),
            new SimpleMeterRegistry());

    private static RuleDefinition definition(String ruleId, String version, String threshold, Instant from,
                                             boolean enabled, String logicKey) {
        return RuleDefinition.create(new RuleDefinition.NewVersion(ruleId, version, "HR rule " + version,
                RuleCategory.CARDIOVASCULAR, ClinicalSeverity.URGENT, logicKey,
                "{\"match\":\"ANY\",\"conditions\":[{\"vitalSign\":\"HEART_RATE\",\"operator\":\"GT\",\"threshold\":"
                        + threshold + "}]}", "d", "a", "[]", "test", "nobody", true, from, null, enabled, null, null),
                "tester", from);
    }

    @Test
    void evaluatesOnlyTheNewestEffectiveVersionPerRule() {
        RuleDefinition v1 = definition("HR-1", "1", "130", Instant.parse("2026-01-01T00:00:00Z"), true,
                VitalSignThresholdRuleLogic.KEY);
        RuleDefinition v2 = definition("HR-1", "2", "100", Instant.parse("2026-03-01T00:00:00Z"), true,
                VitalSignThresholdRuleLogic.KEY);
        when(repository.findEffectiveAt(any())).thenReturn(List.of(v2, v1));

        RuleEvaluationReport report = engine.evaluate(ContextFixtures.context().vital(VitalSignType.HEART_RATE, "110").build());

        assertThat(report.executedRules()).singleElement().satisfies(r -> assertThat(r.version()).isEqualTo("2"));
        assertThat(report.results()).singleElement().satisfies(r -> assertThat(r.triggered()).isTrue());
    }

    @Test
    void skipsDisabledRulesAndReportsThem() {
        when(repository.findEffectiveAt(any())).thenReturn(List.of(
                definition("HR-2", "1", "100", Instant.parse("2026-01-01T00:00:00Z"), false, VitalSignThresholdRuleLogic.KEY)));
        RuleEvaluationReport report = engine.evaluate(ContextFixtures.context().vital(VitalSignType.HEART_RATE, "150").build());
        assertThat(report.results()).isEmpty();
        assertThat(report.disabledRuleIds()).containsExactly("HR-2");
    }

    @Test
    void misconfiguredRuleIsSurfacedAsEvaluationErrorNotSilentlyIgnored() {
        when(repository.findEffectiveAt(any())).thenReturn(List.of(
                definition("BROKEN-1", "1", "100", Instant.parse("2026-01-01T00:00:00Z"), true, "unknown-logic")));
        RuleEvaluationReport report = engine.evaluate(ContextFixtures.context().build());
        assertThat(report.triggered()).singleElement().satisfies(r -> {
            assertThat(r.evaluationError()).isTrue();
            assertThat(r.severity()).isEqualTo(ClinicalSeverity.WARNING);
        });
    }
}
