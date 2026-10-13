package com.medassist.clinicalrules.api;

import com.medassist.clinicalrules.application.DerivedSafetyAlert;
import com.medassist.clinicalrules.application.LiveSafetyCheckService;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Safety alerts")
@RequireClinician
public class SafetyCheckController {

    private final LiveSafetyCheckService service;

    public SafetyCheckController(LiveSafetyCheckService service) {
        this.service = service;
    }

    public record SafetyCheckResponse(Instant evaluatedAt, List<DerivedSafetyAlert> alerts, int rulesEvaluated,
                                      List<String> disabledRules, List<RuleResult> ruleResults,
                                      List<ScoreResult> scores) {
    }

    @GetMapping("/api/encounters/{encounterId}/safety-alerts")
    @Operation(summary = "Deterministic safety rules and scores on current data (no AI involved)")
    public SafetyCheckResponse evaluate(@PathVariable UUID encounterId) {
        LiveSafetyCheckService.SafetyCheck check = service.evaluate(encounterId);
        return new SafetyCheckResponse(check.evaluatedAt(), check.alerts(), check.rules().executedRules().size(),
                check.rules().disabledRuleIds(), check.rules().results(), check.scores());
    }
}
