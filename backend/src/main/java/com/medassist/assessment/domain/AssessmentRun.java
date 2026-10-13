package com.medassist.assessment.domain;

import com.medassist.common.domain.AuditableEntity;
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

/** Mutable progress record of one asynchronous assessment execution. The result is an immutable version. */
@Entity
@Table(name = "assessment_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentRun extends AuditableEntity {

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(name = "encounter_id", nullable = false, updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssessmentRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 40)
    private AssessmentStage currentStage;

    /** JSON array of {stage, at} entries. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stage_history", nullable = false)
    private String stageHistory;

    @Column(name = "include_ai", nullable = false, updatable = false)
    private boolean includeAi;

    @Column(name = "requested_by", nullable = false, updatable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 100, updatable = false)
    private String correlationId;

    private AssessmentRun(UUID id, UUID assessmentId, UUID encounterId, UUID patientId, boolean includeAi,
                          String requestedBy, Instant requestedAt, String correlationId, String initialHistory) {
        super(id);
        this.assessmentId = Objects.requireNonNull(assessmentId);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.patientId = Objects.requireNonNull(patientId);
        this.includeAi = includeAi;
        this.requestedBy = Objects.requireNonNull(requestedBy);
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.correlationId = correlationId;
        this.status = AssessmentRunStatus.QUEUED;
        this.currentStage = AssessmentStage.QUEUED;
        this.stageHistory = initialHistory;
    }

    public static AssessmentRun queue(UUID assessmentId, UUID encounterId, UUID patientId, boolean includeAi,
                                      String requestedBy, Instant requestedAt, String correlationId,
                                      String initialHistoryJson) {
        return new AssessmentRun(UUID.randomUUID(), assessmentId, encounterId, patientId, includeAi, requestedBy,
                requestedAt, correlationId, initialHistoryJson);
    }

    public void advance(AssessmentStage stage, String updatedHistoryJson) {
        this.status = AssessmentRunStatus.RUNNING;
        this.currentStage = stage;
        this.stageHistory = updatedHistoryJson;
    }

    public void complete(UUID versionId, Instant at, String updatedHistoryJson) {
        this.status = AssessmentRunStatus.COMPLETED;
        this.currentStage = AssessmentStage.COMPLETED;
        this.versionId = versionId;
        this.completedAt = at;
        this.stageHistory = updatedHistoryJson;
    }

    public void fail(String reason, Instant at, String updatedHistoryJson) {
        this.status = AssessmentRunStatus.FAILED;
        this.currentStage = AssessmentStage.FAILED;
        this.failureReason = reason;
        this.completedAt = at;
        this.stageHistory = updatedHistoryJson;
    }

    public boolean isActive() {
        return status == AssessmentRunStatus.QUEUED || status == AssessmentRunStatus.RUNNING;
    }
}
