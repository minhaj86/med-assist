package com.medassist.clinicalrules.api;

import com.medassist.clinicalrules.application.RuleAdministrationService;
import com.medassist.clinicalrules.domain.RuleDefinition;
import com.medassist.clinicalrules.engine.ClinicalRuleEngine;
import com.medassist.common.json.JsonCodec;
import com.medassist.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/admin/rules")
@Tag(name = "Admin - Clinical rules")
@RequireAdmin
public class RuleAdminController {

    private final RuleAdministrationService service;
    private final ClinicalRuleEngine engine;
    private final JsonCodec jsonCodec;
    private final Clock clock;

    public RuleAdminController(RuleAdministrationService service, ClinicalRuleEngine engine, JsonCodec jsonCodec,
                               Clock clock) {
        this.service = service;
        this.engine = engine;
        this.jsonCodec = jsonCodec;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "All rule versions (newest first per rule), with governance metadata")
    public List<RuleDtos.RuleDefinitionResponse> list() {
        Instant now = Instant.now(clock);
        return service.listAllVersions().stream().map(d -> toResponse(d, now)).toList();
    }

    @GetMapping("/logic")
    @Operation(summary = "Reviewed rule logic implementations available for rule definitions")
    public List<String> logicKeys() {
        return engine.availableLogicKeys();
    }

    @PutMapping("/{definitionId}/enabled")
    @Operation(summary = "Enable or disable a rule version (operational switch; definition is unchanged)")
    public RuleDtos.RuleDefinitionResponse setEnabled(@PathVariable UUID definitionId,
                                                      @Valid @RequestBody RuleDtos.SetEnabledRequest request) {
        return toResponse(service.setEnabled(definitionId, request.enabled(), request.reason()), Instant.now(clock));
    }

    @PostMapping("/{ruleId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a new version of a rule (historic versions are never overwritten)")
    public RuleDtos.RuleDefinitionResponse publishVersion(@PathVariable String ruleId,
                                                          @Valid @RequestBody RuleDtos.NewVersionRequest r) {
        RuleDefinition created = service.publishNewVersion(ruleId, new RuleAdministrationService.NewVersionCommand(
                r.version(), r.name(), r.severity(), r.parameters(), r.description(), r.recommendedAction(),
                r.references(), r.source(), r.approvedBy(), r.demonstrationOnly(), r.effectiveFrom(),
                r.changeReason(), r.enabled()));
        return toResponse(created, Instant.now(clock));
    }

    private RuleDtos.RuleDefinitionResponse toResponse(RuleDefinition d, Instant now) {
        RuleDtos.LifecycleStatus status = d.isEffectiveAt(now) ? RuleDtos.LifecycleStatus.ACTIVE
                : d.getEffectiveFrom().isAfter(now) ? RuleDtos.LifecycleStatus.SCHEDULED
                : RuleDtos.LifecycleStatus.SUPERSEDED;
        return new RuleDtos.RuleDefinitionResponse(d.getId(), d.getRuleId(), d.getVersion(), d.getName(),
                d.getCategory(), d.getSeverity(), d.getLogicKey(), jsonCodec.readTree(d.getParameters()),
                d.getDescription(), d.getRecommendedAction(),
                jsonCodec.read(d.getReferences(), new TypeReference<List<String>>() {
                }), d.getSource(), d.getApprovedBy(), d.isDemonstrationOnly(), d.getEffectiveFrom(),
                d.getEffectiveUntil(), d.isEnabled(), status, d.getSupersedesId(), d.getChangeReason(),
                d.getCreatedAt(), d.getCreatedBy());
    }
}
