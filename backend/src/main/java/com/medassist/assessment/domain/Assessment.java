package com.medassist.assessment.domain;

import com.medassist.common.domain.AuditableEntity;
import com.medassist.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The clinical assessment of one encounter. Holds the sequence of immutable assessment versions (by number) and
 * the clinician's final, explicitly finalized assessment.
 */
@Entity
@Table(name = "assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Assessment extends AuditableEntity {

    @Column(name = "encounter_id", nullable = false, updatable = false, unique = true)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 30)
    private AssessmentWorkflowStatus workflowStatus;

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber;

    @Column(name = "final_summary", length = 4000)
    private String finalSummary;

    @Column(name = "final_plan", length = 4000)
    private String finalPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "final_diagnoses")
    private String finalDiagnoses;

    @Column(name = "finalized_version_id")
    private UUID finalizedVersionId;

    @Column(name = "finalized_by", length = 100)
    private String finalizedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "clinician_attestation", nullable = false)
    private boolean clinicianAttestation;

    private Assessment(UUID id, UUID encounterId, UUID patientId) {
        super(id);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.patientId = Objects.requireNonNull(patientId);
        this.workflowStatus = AssessmentWorkflowStatus.IN_PROGRESS;
    }

    public static Assessment open(UUID encounterId, UUID patientId) {
        return new Assessment(UUID.randomUUID(), encounterId, patientId);
    }

    public int allocateNextVersionNumber() {
        ensureNotFinalized();
        latestVersionNumber++;
        return latestVersionNumber;
    }

    public boolean isFinalized() {
        return workflowStatus == AssessmentWorkflowStatus.FINALIZED;
    }

    public void ensureNotFinalized() {
        if (isFinalized()) {
            throw new ConflictException("The clinical assessment for this encounter has been finalized");
        }
    }

    /** Explicit clinician action; the only way an assessment becomes final. */
    public void finalizeAssessment(UUID reviewedVersionId, String summary, String plan, String diagnosesJson,
                                   String clinicianId, Instant at, boolean attestation) {
        ensureNotFinalized();
        if (!attestation) {
            throw new IllegalArgumentException("Clinician attestation is required to finalize");
        }
        this.finalizedVersionId = reviewedVersionId;
        this.finalSummary = summary;
        this.finalPlan = plan;
        this.finalDiagnoses = diagnosesJson;
        this.finalizedBy = Objects.requireNonNull(clinicianId);
        this.finalizedAt = Objects.requireNonNull(at);
        this.clinicianAttestation = true;
        this.workflowStatus = AssessmentWorkflowStatus.FINALIZED;
    }
}
