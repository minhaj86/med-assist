package com.medassist.assessment.domain;

import com.medassist.common.domain.BaseEntity;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An immutable assessment version: the exact input snapshot, the deterministic results, the retrieved evidence, the
 * validated AI reasoning and what changed since the previous version. Never updated after creation (database
 * triggers reject UPDATE and DELETE on this table and all its child tables).
 */
@Entity
@Immutable
@Table(name = "assessment_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentVersion extends BaseEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false)
    private String inputSnapshot;

    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes_from_previous", nullable = false)
    private String changesFromPrevious;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 30)
    private AiReasoningStatus aiStatus;

    @Column(name = "ai_status_message", length = 500)
    private String aiStatusMessage;

    @Column(name = "ai_execution_id")
    private UUID aiExecutionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "uncertainties", nullable = false)
    private String uncertainties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_questions", nullable = false)
    private String additionalQuestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_issues", nullable = false)
    private String validationIssues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_quality_warnings", nullable = false)
    private String dataQualityWarnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "executed_rules", nullable = false)
    private String executedRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disabled_rules", nullable = false)
    private String disabledRules;

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<RuleResultRecord> ruleResults = new ArrayList<>();

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<ScoreResultRecord> scoreResults = new ArrayList<>();

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<SafetyAlertRecord> safetyAlerts = new ArrayList<>();

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id", nullable = false, updatable = false)
    @OrderBy("displayOrder")
    @BatchSize(size = 50)
    private List<RetrievedEvidenceRecord> retrievedEvidence = new ArrayList<>();

    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_version_id", nullable = false, updatable = false)
    @OrderBy("rank")
    @BatchSize(size = 50)
    private List<DifferentialDiagnosisRecord> differentials = new ArrayList<>();

    public record Header(UUID assessmentId, UUID encounterId, UUID patientId, int versionNumber, UUID runId,
                         Instant createdAt, String createdBy, String inputSnapshot, String inputHash,
                         String changesFromPrevious, AiReasoningStatus aiStatus, String aiStatusMessage,
                         UUID aiExecutionId, String uncertainties, String additionalQuestions,
                         String validationIssues, String dataQualityWarnings, String executedRules,
                         String disabledRules) {
    }

    public static AssessmentVersion create(Header h, List<RuleResultRecord> ruleResults,
                                           List<ScoreResultRecord> scoreResults, List<SafetyAlertRecord> alerts,
                                           List<RetrievedEvidenceRecord> evidence,
                                           List<DifferentialDiagnosisRecord> differentials) {
        AssessmentVersion v = new AssessmentVersion(UUID.randomUUID());
        v.assessmentId = h.assessmentId();
        v.encounterId = h.encounterId();
        v.patientId = h.patientId();
        v.versionNumber = h.versionNumber();
        v.runId = h.runId();
        v.createdAt = h.createdAt();
        v.createdBy = h.createdBy();
        v.inputSnapshot = h.inputSnapshot();
        v.inputHash = h.inputHash();
        v.changesFromPrevious = h.changesFromPrevious();
        v.aiStatus = h.aiStatus();
        v.aiStatusMessage = h.aiStatusMessage();
        v.aiExecutionId = h.aiExecutionId();
        v.uncertainties = h.uncertainties();
        v.additionalQuestions = h.additionalQuestions();
        v.validationIssues = h.validationIssues();
        v.dataQualityWarnings = h.dataQualityWarnings();
        v.executedRules = h.executedRules();
        v.disabledRules = h.disabledRules();
        v.ruleResults = new ArrayList<>(ruleResults);
        v.scoreResults = new ArrayList<>(scoreResults);
        v.safetyAlerts = new ArrayList<>(alerts);
        v.retrievedEvidence = new ArrayList<>(evidence);
        v.differentials = new ArrayList<>(differentials);
        return v;
    }

    private AssessmentVersion(UUID id) {
        super(id);
    }
}
