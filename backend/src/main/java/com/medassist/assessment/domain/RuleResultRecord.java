package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import com.medassist.common.domain.ClinicalSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "clinical_rule_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RuleResultRecord extends BaseEntity {

    @Column(name = "rule_definition_id", nullable = false)
    private UUID ruleDefinitionId;

    @Column(name = "rule_id", nullable = false, length = 60)
    private String ruleId;

    @Column(name = "rule_version", nullable = false, length = 30)
    private String ruleVersion;

    @Column(name = "rule_name", nullable = false, length = 200)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ClinicalSeverity severity;

    @Column(name = "triggered", nullable = false)
    private boolean triggered;

    @Column(name = "evaluation_error", nullable = false)
    private boolean evaluationError;

    @Column(name = "explanation", nullable = false, length = 2000)
    private String explanation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_observations", nullable = false)
    private String supportingObservations;

    @Column(name = "recommended_action", length = 1000)
    private String recommendedAction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_references", nullable = false)
    private String references;

    @Column(name = "demonstration_only", nullable = false)
    private boolean demonstrationOnly;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public RuleResultRecord(UUID ruleDefinitionId, String ruleId, String ruleVersion, String ruleName,
                            ClinicalSeverity severity, boolean triggered, boolean evaluationError, String explanation,
                            String supportingObservationsJson, String recommendedAction, String referencesJson,
                            boolean demonstrationOnly, int displayOrder) {
        super(UUID.randomUUID());
        this.ruleDefinitionId = ruleDefinitionId;
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.ruleName = ruleName;
        this.severity = severity;
        this.triggered = triggered;
        this.evaluationError = evaluationError;
        this.explanation = explanation;
        this.supportingObservations = supportingObservationsJson;
        this.recommendedAction = recommendedAction;
        this.references = referencesJson;
        this.demonstrationOnly = demonstrationOnly;
        this.displayOrder = displayOrder;
    }
}
