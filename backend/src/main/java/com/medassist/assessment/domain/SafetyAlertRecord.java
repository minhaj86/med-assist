package com.medassist.assessment.domain;

import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.domain.BaseEntity;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.differential.SupportStatus;
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

/** A safety alert in an assessment version, always labelled with its origin (rule, score, AI ...). */
@Entity
@Immutable
@Table(name = "safety_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SafetyAlertRecord extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private AlertOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private ClinicalSeverity severity;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "explanation", length = 2000)
    private String explanation;

    @Column(name = "recommended_action", length = 1000)
    private String recommendedAction;

    @Column(name = "source_reference", length = 200)
    private String sourceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_fact_keys", nullable = false)
    private String supportingFactKeys;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_keys", nullable = false)
    private String evidenceKeys;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_status", nullable = false, length = 20)
    private SupportStatus supportStatus;

    @Column(name = "demonstration_only", nullable = false)
    private boolean demonstrationOnly;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public SafetyAlertRecord(AlertOrigin origin, ClinicalSeverity severity, String title, String explanation,
                             String recommendedAction, String sourceReference, String supportingFactKeysJson,
                             String evidenceKeysJson, SupportStatus supportStatus, boolean demonstrationOnly,
                             int displayOrder) {
        super(UUID.randomUUID());
        this.origin = origin;
        this.severity = severity;
        this.title = title;
        this.explanation = explanation;
        this.recommendedAction = recommendedAction;
        this.sourceReference = sourceReference;
        this.supportingFactKeys = supportingFactKeysJson;
        this.evidenceKeys = evidenceKeysJson;
        this.supportStatus = supportStatus;
        this.demonstrationOnly = demonstrationOnly;
        this.displayOrder = displayOrder;
    }
}
