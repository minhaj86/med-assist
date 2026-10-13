package com.medassist.investigation.domain;

import com.medassist.assessment.domain.ProposedBy;
import com.medassist.common.domain.AuditableEntity;
import com.medassist.common.error.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An investigation in the encounter's workflow (separate from immutable suggestions in assessment versions). */
@Entity
@Table(name = "investigations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Investigation extends AuditableEntity {

    @Column(name = "encounter_id", nullable = false, updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "proposed_by", nullable = false, length = 20)
    private ProposedBy proposedBy;

    @Column(name = "reason", length = 2000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_evidence", nullable = false)
    private String supportingEvidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvestigationStatus status;

    @Column(name = "source_suggestion_id", updatable = false)
    private UUID sourceSuggestionId;

    @Column(name = "source_assessment_version_id", updatable = false)
    private UUID sourceAssessmentVersionId;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    public static Investigation create(UUID encounterId, UUID patientId, String name, ProposedBy proposedBy,
                                       String reason, String supportingEvidenceJson, InvestigationStatus status,
                                       UUID sourceSuggestionId, UUID sourceAssessmentVersionId, String createdBy) {
        if (status != InvestigationStatus.ACCEPTED && status != InvestigationStatus.ORDERED
                && status != InvestigationStatus.REJECTED) {
            throw new DomainRuleViolationException("Investigations enter the workflow as ACCEPTED, ORDERED or REJECTED");
        }
        Investigation i = new Investigation(UUID.randomUUID());
        i.encounterId = Objects.requireNonNull(encounterId);
        i.patientId = Objects.requireNonNull(patientId);
        i.name = Objects.requireNonNull(name);
        i.proposedBy = Objects.requireNonNull(proposedBy);
        i.reason = reason;
        i.supportingEvidence = supportingEvidenceJson;
        i.status = status;
        i.sourceSuggestionId = sourceSuggestionId;
        i.sourceAssessmentVersionId = sourceAssessmentVersionId;
        i.createdBy = Objects.requireNonNull(createdBy);
        return i;
    }

    private Investigation(UUID id) {
        super(id);
    }

    public InvestigationStatus transitionTo(InvestigationStatus next) {
        if (!status.allowedNext().contains(next)) {
            throw new DomainRuleViolationException("Cannot change investigation status from " + status + " to " + next);
        }
        InvestigationStatus previous = status;
        status = next;
        return previous;
    }
}
