package com.medassist.clinicalrules.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.clinicalrules.engine.ClinicalRuleEngine;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContextAssembler;
import com.medassist.diagnosis.scoring.ClinicalScoringService;
import com.medassist.diagnosis.scoring.ScoreResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Evaluates deterministic safety rules and scores on the current encounter data, on demand. Works with no AI
 * provider configured and never waits for one.
 */
@Service
public class LiveSafetyCheckService {

    public record SafetyCheck(Instant evaluatedAt, List<DerivedSafetyAlert> alerts, RuleEvaluationReport rules,
                              List<ScoreResult> scores) {
    }

    private final ClinicalContextAssembler assembler;
    private final ClinicalRuleEngine ruleEngine;
    private final ClinicalScoringService scoringService;
    private final SafetyAlertDerivation derivation;
    private final AuditService auditService;

    public LiveSafetyCheckService(ClinicalContextAssembler assembler, ClinicalRuleEngine ruleEngine,
                                  ClinicalScoringService scoringService, SafetyAlertDerivation derivation,
                                  AuditService auditService) {
        this.assembler = assembler;
        this.ruleEngine = ruleEngine;
        this.scoringService = scoringService;
        this.derivation = derivation;
        this.auditService = auditService;
    }

    public SafetyCheck evaluate(UUID encounterId) {
        ClinicalContext context = assembler.assemble(encounterId);
        RuleEvaluationReport report = ruleEngine.evaluate(context);
        List<ScoreResult> scores = scoringService.calculateAll(context);
        List<DerivedSafetyAlert> alerts = derivation.derive(report, scores);
        auditService.recordIndependently(AuditEntry.of(AuditEventType.SAFETY_RULES_EVALUATED)
                .entity("Encounter", encounterId)
                .patient(context.patientId())
                .encounter(encounterId)
                .with("mode", "LIVE")
                .with("rulesEvaluated", report.executedRules().size())
                .with("triggered", report.triggered().stream().map(r -> r.ruleId() + "@" + r.ruleVersion()).toList())
                .with("disabledRules", report.disabledRuleIds()));
        return new SafetyCheck(context.assembledAt(), alerts, report, scores);
    }
}
