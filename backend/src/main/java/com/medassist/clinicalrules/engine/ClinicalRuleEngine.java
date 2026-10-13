package com.medassist.clinicalrules.engine;

import com.medassist.clinicalrules.domain.ClinicalRule;
import com.medassist.clinicalrules.domain.RuleDefinition;
import com.medassist.clinicalrules.domain.RuleDefinitionRepository;
import com.medassist.clinicalrules.domain.RuleLogic;
import com.medassist.clinicalrules.domain.RuleMetadata;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.common.json.JsonCodec;
import com.medassist.diagnosis.context.ClinicalContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

/**
 * Deterministic clinical safety rule engine. Evaluates the rule version effective at the context's assembly time
 * for every enabled rule. Independent of the AI subsystem: it never calls, and is never bypassed by, an LLM.
 */
@Service
public class ClinicalRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ClinicalRuleEngine.class);

    private final RuleDefinitionRepository repository;
    private final Map<String, RuleLogic> logicByKey;
    private final JsonCodec jsonCodec;
    private final Timer timer;

    public ClinicalRuleEngine(RuleDefinitionRepository repository, List<RuleLogic> logics, JsonCodec jsonCodec,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.logicByKey = logics.stream().collect(Collectors.toMap(RuleLogic::logicKey, Function.identity()));
        this.jsonCodec = jsonCodec;
        this.timer = Timer.builder("clinical_rule_execution_duration")
                .description("Time to evaluate all clinical safety rules for one context")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public RuleEvaluationReport evaluate(ClinicalContext context) {
        long start = System.nanoTime();
        Map<String, RuleDefinition> current = new LinkedHashMap<>();
        for (RuleDefinition definition : repository.findEffectiveAt(context.assembledAt())) {
            current.putIfAbsent(definition.getRuleId(), definition); // newest effective version first
        }
        List<RuleResult> results = new ArrayList<>();
        List<RuleEvaluationReport.ExecutedRule> executed = new ArrayList<>();
        List<String> disabled = new ArrayList<>();
        for (RuleDefinition definition : current.values()) {
            if (!definition.isEnabled()) {
                disabled.add(definition.getRuleId());
                continue;
            }
            executed.add(new RuleEvaluationReport.ExecutedRule(definition.getId(), definition.getRuleId(),
                    definition.getVersion()));
            results.add(evaluateOne(definition, context));
        }
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        timer.record(duration);
        return new RuleEvaluationReport(context.assembledAt(), List.copyOf(results), List.copyOf(executed),
                List.copyOf(disabled), duration);
    }

    /** Binds a definition to its logic; used for evaluation and to validate new rule versions. */
    public ClinicalRule bind(RuleDefinition definition) {
        RuleLogic logic = logicByKey.get(definition.getLogicKey());
        if (logic == null) {
            throw new IllegalArgumentException("Unknown rule logic '" + definition.getLogicKey() + "'");
        }
        List<String> references = jsonCodec.read(definition.getReferences(), new TypeReference<List<String>>() {
        });
        RuleMetadata metadata = definition.toMetadata(references);
        return logic.bind(metadata, jsonCodec.readTree(definition.getParameters()));
    }

    public List<String> availableLogicKeys() {
        return logicByKey.keySet().stream().sorted().toList();
    }

    private RuleResult evaluateOne(RuleDefinition definition, ClinicalContext context) {
        try {
            return bind(definition).evaluate(context);
        } catch (RuntimeException e) {
            log.error("Clinical rule {} v{} could not be evaluated", definition.getRuleId(), definition.getVersion(), e);
            return RuleResult.error(definition.toMetadata(List.of()),
                    "Rule could not be evaluated: configuration or evaluation error");
        }
    }
}
