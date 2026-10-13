package com.medassist.assessment.application;

import com.medassist.assessment.domain.AssessmentRunStatus;
import com.medassist.assessment.domain.AssessmentStage;
import com.medassist.assessment.domain.AssessmentWorkflowStatus;
import com.medassist.assessment.domain.EvidenceType;
import com.medassist.assessment.domain.FeedbackAction;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.assessment.domain.ProposedBy;
import com.medassist.assessment.domain.ReviewState;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.diagnosis.differential.AssessmentMethod;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read models returned by the assessment API. */
public final class AssessmentViews {

    private AssessmentViews() {
    }

    public record RunView(UUID runId, UUID assessmentId, UUID encounterId, AssessmentRunStatus status,
                          AssessmentStage currentStage, List<AssessmentRunTracker.StageEntry> stages,
                          boolean includeAi, Instant requestedAt, Instant completedAt, UUID versionId,
                          String failureReason) {
    }

    public record VersionSummary(UUID id, UUID encounterId, int versionNumber, Instant createdAt, String createdBy,
                                 AiReasoningStatus aiStatus, int criticalAlerts, int urgentAlerts, int alertCount,
                                 int differentialCount, String leadingConsideration, int inputChangeCount) {
    }

    public record FeedbackView(FeedbackAction action, String clinicianId, Instant at, String reason,
                               String modifiedText) {
    }

    public record AlertView(UUID id, AlertOrigin origin, ClinicalSeverity severity, String title, String explanation,
                            String recommendedAction, String sourceReference, List<String> supportingFactKeys,
                            List<String> evidenceKeys, SupportStatus supportStatus, boolean demonstrationOnly,
                            ReviewState reviewState, FeedbackView latestFeedback) {
    }

    public record RuleResultView(String ruleId, String ruleVersion, String ruleName, ClinicalSeverity severity,
                                 boolean triggered, boolean evaluationError, String explanation,
                                 List<SupportingObservation> supportingObservations, String recommendedAction,
                                 List<String> references, boolean demonstrationOnly) {
    }

    public record EvidenceView(String evidenceKey, UUID chunkId, UUID documentId, String documentTitle,
                               String documentVersion, String sourceOrganization, String section, Integer page,
                               LocalDate publicationDate, BigDecimal retrievalSimilarity, String excerpt,
                               boolean syntheticSource) {
    }

    public record StatementView(UUID id, EvidenceType type, String text, List<String> factKeys,
                                List<String> evidenceKeys, SupportStatus support) {
    }

    public record InvestigationSuggestionView(UUID id, String name, String reason, ProposedBy proposedBy,
                                              List<String> evidenceKeys, SupportStatus support,
                                              ReviewState reviewState, FeedbackView latestFeedback) {
    }

    public record DifferentialView(UUID id, int rank, String rankBasis, String conditionName,
                                   CodingSystem codingSystem, String code, ConceptVerificationStatus codeVerification,
                                   ConfidenceLevel confidence, ConfidenceLevel originalConfidence,
                                   AssessmentMethod method, GroundingStatus grounding, String whyConsidered,
                                   List<StatementView> supportingEvidence, List<StatementView> contradictingEvidence,
                                   List<StatementView> missingInformation,
                                   List<InvestigationSuggestionView> suggestedInvestigations,
                                   String distinguishingFeatures, String reasoningSummary, List<String> citations,
                                   List<String> validationNotes, ReviewState reviewState,
                                   FeedbackView latestFeedback) {
    }

    public record FactView(String key, String category, String description, String provenance) {
    }

    public record AiStatusView(AiReasoningStatus status, String message, UUID executionId) {
    }

    public record VersionView(UUID id, UUID assessmentId, UUID encounterId, UUID patientId, int versionNumber,
                              Instant createdAt, String createdBy, AssessmentWorkflowStatus workflowStatus,
                              String inputHash, AiStatusView ai, List<FactView> facts, AssessmentChanges changes,
                              List<AlertView> safetyAlerts, List<AlertView> aiRedFlags,
                              List<RuleResultView> ruleResults, List<ScoreResult> scores,
                              List<EvidenceView> evidence, List<DifferentialView> differentials,
                              List<String> uncertainties, List<String> additionalQuestions,
                              List<String> dataQualityWarnings, List<String> validationIssues,
                              List<RuleEvaluationReport.ExecutedRule> executedRules, List<String> disabledRules) {
    }

    public record FinalAssessmentView(UUID assessmentId, UUID encounterId, AssessmentWorkflowStatus status,
                                      UUID finalizedVersionId, String summary, String plan,
                                      List<FinalDiagnosis> diagnoses, String finalizedBy, Instant finalizedAt) {
    }
}
