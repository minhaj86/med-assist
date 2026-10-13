package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
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

/**
 * An investigation proposed within an assessment version (status SUGGESTED by definition). Clinician decisions
 * (accept / reject / order / complete) are tracked separately in the investigation workflow; proposals are never
 * orders.
 */
@Entity
@Immutable
@Table(name = "suggested_investigations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuggestedInvestigationRecord extends BaseEntity {

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_by", nullable = false, length = 20)
    private ProposedBy proposedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_evidence_keys", nullable = false)
    private String supportingEvidenceKeys;

    @Enumerated(EnumType.STRING)
    @Column(name = "support_status", nullable = false, length = 20)
    private SupportStatus supportStatus;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public SuggestedInvestigationRecord(String name, String reason, ProposedBy proposedBy,
                                        String supportingEvidenceKeysJson, SupportStatus supportStatus,
                                        int displayOrder) {
        super(UUID.randomUUID());
        this.name = name;
        this.reason = reason;
        this.proposedBy = proposedBy;
        this.supportingEvidenceKeys = supportingEvidenceKeysJson;
        this.supportStatus = supportStatus;
        this.displayOrder = displayOrder;
    }
}
