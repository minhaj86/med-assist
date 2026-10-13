package com.medassist.clinicalrules.domain;

import com.medassist.common.domain.BaseEntity;
import com.medassist.common.domain.ClinicalSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable version of a clinical rule. Definitional columns cannot be updated (enforced by a database
 * trigger); only the operational {@code enabled} switch and the end of the effective period may change. A change
 * to a rule always creates a new version.
 */
@Entity
@Table(name = "clinical_rule_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleDefinition extends BaseEntity {

    @Column(name = "rule_id", nullable = false, updatable = false, length = 60)
    private String ruleId;

    @Column(name = "version", nullable = false, updatable = false, length = 30)
    private String version;

    @Column(name = "name", nullable = false, updatable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 30)
    private RuleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false, length = 20)
    private ClinicalSeverity severity;

    @Column(name = "logic_key", nullable = false, updatable = false, length = 80)
    private String logicKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, updatable = false)
    private String parameters;

    @Column(name = "description", nullable = false, updatable = false, length = 2000)
    private String description;

    @Column(name = "recommended_action", nullable = false, updatable = false, length = 1000)
    private String recommendedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_references", nullable = false, updatable = false)
    private String references;

    @Column(name = "source", nullable = false, updatable = false, length = 500)
    private String source;

    @Column(name = "approved_by", nullable = false, updatable = false, length = 200)
    private String approvedBy;

    @Column(name = "demonstration_only", nullable = false, updatable = false)
    private boolean demonstrationOnly;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_until")
    private Instant effectiveUntil;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "supersedes_id", updatable = false)
    private UUID supersedesId;

    @Column(name = "change_reason", updatable = false, length = 1000)
    private String changeReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    public record NewVersion(String ruleId, String version, String name, RuleCategory category,
                             ClinicalSeverity severity, String logicKey, String parametersJson, String description,
                             String recommendedAction, String referencesJson, String source, String approvedBy,
                             boolean demonstrationOnly, Instant effectiveFrom, Instant effectiveUntil,
                             boolean enabled, UUID supersedesId, String changeReason) {
    }

    public static RuleDefinition create(NewVersion v, String createdBy, Instant createdAt) {
        RuleDefinition d = new RuleDefinition(UUID.randomUUID());
        d.ruleId = v.ruleId();
        d.version = v.version();
        d.name = v.name();
        d.category = v.category();
        d.severity = v.severity();
        d.logicKey = v.logicKey();
        d.parameters = v.parametersJson();
        d.description = v.description();
        d.recommendedAction = v.recommendedAction();
        d.references = v.referencesJson();
        d.source = v.source();
        d.approvedBy = v.approvedBy();
        d.demonstrationOnly = v.demonstrationOnly();
        d.effectiveFrom = v.effectiveFrom();
        d.effectiveUntil = v.effectiveUntil();
        d.enabled = v.enabled();
        d.supersedesId = v.supersedesId();
        d.changeReason = v.changeReason();
        d.createdBy = createdBy;
        d.createdAt = createdAt;
        return d;
    }

    private RuleDefinition(UUID id) {
        super(id);
    }

    public boolean isEffectiveAt(Instant at) {
        return !effectiveFrom.isAfter(at) && (effectiveUntil == null || effectiveUntil.isAfter(at));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Ends this version's effective period when it is superseded. */
    public void endEffectivePeriod(Instant until) {
        if (effectiveUntil == null || effectiveUntil.isAfter(until)) {
            this.effectiveUntil = until;
        }
    }

    public RuleMetadata toMetadata(List<String> referenceList) {
        return new RuleMetadata(getId(), ruleId, version, name, category, severity, logicKey, description,
                recommendedAction, referenceList, source, approvedBy, demonstrationOnly, effectiveFrom,
                effectiveUntil);
    }
}
