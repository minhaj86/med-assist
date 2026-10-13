package com.medassist.clinicalrules.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.clinicalrules.domain.RuleDefinition;
import com.medassist.clinicalrules.domain.RuleDefinitionRepository;
import com.medassist.clinicalrules.engine.ClinicalRuleEngine;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.common.error.ConflictException;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Rule governance. Existing rule versions are never modified: administrators can enable/disable a version or
 * publish a new version, which ends the previous version's effective period.
 */
@Service
public class RuleAdministrationService {

    public record NewVersionCommand(String version, String name, ClinicalSeverity severity, JsonNode parameters,
                                    String description, String recommendedAction, List<String> references,
                                    String source, String approvedBy, boolean demonstrationOnly,
                                    Instant effectiveFrom, String changeReason, boolean enabled) {
    }

    private final RuleDefinitionRepository repository;
    private final ClinicalRuleEngine ruleEngine;
    private final JsonCodec jsonCodec;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public RuleAdministrationService(RuleDefinitionRepository repository, ClinicalRuleEngine ruleEngine,
                                     JsonCodec jsonCodec, AuditService auditService,
                                     CurrentUserProvider currentUser, Clock clock) {
        this.repository = repository;
        this.ruleEngine = ruleEngine;
        this.jsonCodec = jsonCodec;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RuleDefinition> listAllVersions() {
        return repository.findAllByOrderByRuleIdAscEffectiveFromDesc();
    }

    @Transactional
    public RuleDefinition setEnabled(UUID definitionId, boolean enabled, String reason) {
        RuleDefinition definition = repository.findById(definitionId)
                .orElseThrow(() -> NotFoundException.of("Clinical rule definition", definitionId));
        definition.setEnabled(enabled);
        auditService.record(AuditEntry.of(enabled ? AuditEventType.CLINICAL_RULE_ENABLED : AuditEventType.CLINICAL_RULE_DISABLED)
                .entity("ClinicalRuleDefinition", definitionId)
                .with("ruleId", definition.getRuleId())
                .with("version", definition.getVersion())
                .with("reason", reason));
        return definition;
    }

    @Transactional
    public RuleDefinition publishNewVersion(String ruleId, NewVersionCommand command) {
        List<RuleDefinition> versions = repository.findByRuleIdOrderByEffectiveFromDesc(ruleId);
        if (versions.isEmpty()) {
            throw new NotFoundException("Clinical rule", ruleId);
        }
        if (repository.existsByRuleIdAndVersion(ruleId, command.version())) {
            throw new ConflictException("Rule " + ruleId + " already has a version " + command.version());
        }
        Instant now = Instant.now(clock);
        Instant effectiveFrom = command.effectiveFrom() == null ? now : command.effectiveFrom();
        if (effectiveFrom.isBefore(now.minus(Duration.ofMinutes(1)))) {
            throw new DomainRuleViolationException("A new rule version cannot be back-dated");
        }
        RuleDefinition latest = versions.getFirst();
        if (!effectiveFrom.isAfter(latest.getEffectiveFrom())) {
            throw new DomainRuleViolationException("New version must become effective after the latest version");
        }
        RuleDefinition created = RuleDefinition.create(new RuleDefinition.NewVersion(ruleId, command.version(),
                command.name() == null ? latest.getName() : command.name(), latest.getCategory(), command.severity(),
                latest.getLogicKey(), jsonCodec.write(command.parameters()), command.description(),
                command.recommendedAction(), jsonCodec.write(command.references() == null ? List.of() : command.references()),
                command.source(), command.approvedBy(), command.demonstrationOnly(), effectiveFrom, null,
                command.enabled(), latest.getId(), command.changeReason()), currentUser.currentUserId(), now);
        try {
            ruleEngine.bind(created);
        } catch (IllegalArgumentException e) {
            throw new DomainRuleViolationException("Invalid rule parameters: " + e.getMessage());
        }
        for (RuleDefinition version : versions) {
            version.endEffectivePeriod(effectiveFrom);
        }
        repository.save(created);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_RULE_VERSION_CREATED)
                .entity("ClinicalRuleDefinition", created.getId())
                .with("ruleId", ruleId)
                .with("version", command.version())
                .with("supersedes", latest.getVersion())
                .with("effectiveFrom", effectiveFrom.toString()));
        return created;
    }
}
