package com.medassist.assessment.application;

import com.medassist.assessment.application.AssessmentViews.AlertView;
import com.medassist.assessment.application.AssessmentViews.DifferentialView;
import com.medassist.assessment.application.AssessmentViews.EvidenceView;
import com.medassist.assessment.application.AssessmentViews.FactView;
import com.medassist.assessment.application.AssessmentViews.FeedbackView;
import com.medassist.assessment.application.AssessmentViews.InvestigationSuggestionView;
import com.medassist.assessment.application.AssessmentViews.RuleResultView;
import com.medassist.assessment.application.AssessmentViews.StatementView;
import com.medassist.assessment.domain.Assessment;
import com.medassist.assessment.domain.AssessmentRepository;
import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentRunRepository;
import com.medassist.assessment.domain.AssessmentVersion;
import com.medassist.assessment.domain.AssessmentVersionRepository;
import com.medassist.assessment.domain.ClinicianFeedback;
import com.medassist.assessment.domain.ClinicianFeedbackRepository;
import com.medassist.assessment.domain.DiagnosticEvidenceRecord;
import com.medassist.assessment.domain.DifferentialDiagnosisRecord;
import com.medassist.assessment.domain.EvidenceType;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.assessment.domain.ReviewState;
import com.medassist.assessment.domain.RuleResultRecord;
import com.medassist.assessment.domain.SafetyAlertRecord;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.encounter.application.EncounterService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

@Service
public class AssessmentQueryService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AssessmentRepository assessmentRepository;
    private final AssessmentRunRepository runRepository;
    private final AssessmentVersionRepository versionRepository;
    private final ClinicianFeedbackRepository feedbackRepository;
    private final EncounterService encounterService;
    private final AssessmentRunTracker tracker;
    private final AuditService auditService;
    private final JsonCodec json;

    public AssessmentQueryService(AssessmentRepository assessmentRepository, AssessmentRunRepository runRepository,
                                  AssessmentVersionRepository versionRepository,
                                  ClinicianFeedbackRepository feedbackRepository, EncounterService encounterService,
                                  AssessmentRunTracker tracker, AuditService auditService, JsonCodec json) {
        this.assessmentRepository = assessmentRepository;
        this.runRepository = runRepository;
        this.versionRepository = versionRepository;
        this.feedbackRepository = feedbackRepository;
        this.encounterService = encounterService;
        this.tracker = tracker;
        this.auditService = auditService;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public AssessmentViews.RunView run(UUID runId) {
        return toRunView(runRepository.findById(runId).orElseThrow(() -> NotFoundException.of("Assessment run", runId)));
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentViews.RunView> latestRun(UUID encounterId) {
        encounterService.require(encounterId);
        return runRepository.findFirstByEncounterIdOrderByRequestedAtDesc(encounterId).map(this::toRunView);
    }

    @Transactional(readOnly = true)
    public List<AssessmentViews.VersionSummary> versions(UUID encounterId) {
        encounterService.require(encounterId);
        return versionRepository.findByEncounterIdOrderByVersionNumberDesc(encounterId).stream()
                .map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<AssessmentViews.VersionSummary> summaries(List<AssessmentVersion> versions) {
        return versions.stream().map(this::toSummary).toList();
    }

    @Transactional
    public AssessmentViews.VersionView version(UUID versionId) {
        AssessmentVersion v = versionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Assessment version", versionId));
        Assessment assessment = assessmentRepository.findById(v.getAssessmentId()).orElseThrow();
        auditService.record(AuditEntry.of(AuditEventType.ASSESSMENT_VIEWED)
                .entity("AssessmentVersion", versionId)
                .patient(v.getPatientId())
                .encounter(v.getEncounterId())
                .with("versionNumber", v.getVersionNumber()));
        Map<UUID, ClinicianFeedback> latest = new HashMap<>();
        feedbackRepository.findByAssessmentVersionIdOrderByCreatedAtAsc(versionId)
                .forEach(f -> latest.put(f.getSuggestionId(), f));
        boolean finalized = assessment.isFinalized();

        ContextSnapshot snapshot = json.read(v.getInputSnapshot(), ContextSnapshot.class);
        List<FactView> facts = snapshot.facts().stream()
                .map(f -> new FactView(f.key(), f.category().name(), f.description(),
                        f.provenance() == null ? null : f.provenance().name()))
                .toList();
        List<AlertView> deterministic = v.getSafetyAlerts().stream()
                .filter(a -> a.getOrigin() != AlertOrigin.AI)
                .map(a -> toAlert(a, null, false)).toList();
        List<AlertView> aiFlags = v.getSafetyAlerts().stream()
                .filter(a -> a.getOrigin() == AlertOrigin.AI)
                .map(a -> toAlert(a, latest.get(a.getId()), finalized)).toList();
        return new AssessmentViews.VersionView(v.getId(), v.getAssessmentId(), v.getEncounterId(), v.getPatientId(),
                v.getVersionNumber(), v.getCreatedAt(), v.getCreatedBy(), assessment.getWorkflowStatus(),
                v.getInputHash(), new AssessmentViews.AiStatusView(v.getAiStatus(), v.getAiStatusMessage(),
                v.getAiExecutionId()), facts, json.read(v.getChangesFromPrevious(), AssessmentChanges.class),
                deterministic, aiFlags, v.getRuleResults().stream().map(this::toRuleView).toList(),
                v.getScoreResults().stream().map(s -> json.read(s.getResult(), ScoreResult.class)).toList(),
                v.getRetrievedEvidence().stream().map(e -> new EvidenceView(e.getEvidenceKey(), e.getChunkId(),
                        e.getDocumentId(), e.getDocumentTitle(), e.getDocumentVersion(), e.getSourceOrganization(),
                        e.getSection(), e.getPageNumber(), e.getPublicationDate(), e.getSimilarity(), e.getExcerpt(),
                        e.isSyntheticSource())).toList(),
                v.getDifferentials().stream().map(d -> toDifferential(d, latest, finalized)).toList(),
                strings(v.getUncertainties()), strings(v.getAdditionalQuestions()),
                strings(v.getDataQualityWarnings()), strings(v.getValidationIssues()),
                json.read(v.getExecutedRules(), new TypeReference<List<RuleEvaluationReport.ExecutedRule>>() {
                }), strings(v.getDisabledRules()));
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentViews.FinalAssessmentView> finalAssessment(UUID encounterId) {
        encounterService.require(encounterId);
        return assessmentRepository.findByEncounterId(encounterId).map(a -> new AssessmentViews.FinalAssessmentView(
                a.getId(), a.getEncounterId(), a.getWorkflowStatus(), a.getFinalizedVersionId(), a.getFinalSummary(),
                a.getFinalPlan(), a.getFinalDiagnoses() == null ? List.of()
                : json.read(a.getFinalDiagnoses(), new TypeReference<List<FinalDiagnosis>>() {
                }), a.getFinalizedBy(), a.getFinalizedAt()));
    }

    /** Compact text summary of the latest assessment version for the AI assistant's context. */
    @Transactional(readOnly = true)
    public Optional<String> latestSummaryForAssistant(UUID encounterId) {
        return versionRepository.findByEncounterIdOrderByVersionNumberDesc(encounterId).stream().findFirst().map(v -> {
            StringBuilder sb = new StringBuilder("Assessment version ").append(v.getVersionNumber())
                    .append(" (").append(v.getCreatedAt()).append("). AI status: ").append(v.getAiStatus()).append(".\n");
            sb.append("Deterministic safety alerts: ");
            List<SafetyAlertRecord> deterministic = v.getSafetyAlerts().stream()
                    .filter(a -> a.getOrigin() != AlertOrigin.AI).toList();
            sb.append(deterministic.isEmpty() ? "none" : deterministic.stream()
                    .map(a -> "[" + a.getSeverity() + "] " + a.getTitle()).reduce((a, b) -> a + "; " + b).orElse(""));
            sb.append("\nDifferential considerations: ");
            sb.append(v.getDifferentials().isEmpty() ? "none" : v.getDifferentials().stream()
                    .map(d -> d.getConditionName() + " (confidence "
                            + (d.getConfidenceLevel() == null ? "not AI-assessed" : d.getConfidenceLevel())
                            + ", " + d.getGroundingStatus() + ")")
                    .reduce((a, b) -> a + "; " + b).orElse(""));
            return sb.toString();
        });
    }

    private AssessmentViews.RunView toRunView(AssessmentRun run) {
        return new AssessmentViews.RunView(run.getId(), run.getAssessmentId(), run.getEncounterId(), run.getStatus(),
                run.getCurrentStage(), tracker.history(run), run.isIncludeAi(), run.getRequestedAt(),
                run.getCompletedAt(), run.getVersionId(), run.getFailureReason());
    }

    private AssessmentViews.VersionSummary toSummary(AssessmentVersion v) {
        List<SafetyAlertRecord> alerts = v.getSafetyAlerts();
        AssessmentChanges changes = json.read(v.getChangesFromPrevious(), AssessmentChanges.class);
        return new AssessmentViews.VersionSummary(v.getId(), v.getEncounterId(), v.getVersionNumber(),
                v.getCreatedAt(), v.getCreatedBy(), v.getAiStatus(),
                (int) alerts.stream().filter(a -> a.getSeverity() == ClinicalSeverity.CRITICAL).count(),
                (int) alerts.stream().filter(a -> a.getSeverity() == ClinicalSeverity.URGENT).count(),
                alerts.size(), v.getDifferentials().size(),
                v.getDifferentials().isEmpty() ? null : v.getDifferentials().getFirst().getConditionName(),
                changes.inputChanges().size());
    }

    private AlertView toAlert(SafetyAlertRecord a, ClinicianFeedback feedback, boolean finalized) {
        boolean ai = a.getOrigin() == AlertOrigin.AI;
        return new AlertView(a.getId(), a.getOrigin(), a.getSeverity(), a.getTitle(), a.getExplanation(),
                a.getRecommendedAction(), a.getSourceReference(), strings(a.getSupportingFactKeys()),
                strings(a.getEvidenceKeys()), a.getSupportStatus(), a.isDemonstrationOnly(),
                ai ? reviewState(feedback, finalized) : null, toFeedback(feedback));
    }

    private RuleResultView toRuleView(RuleResultRecord r) {
        return new RuleResultView(r.getRuleId(), r.getRuleVersion(), r.getRuleName(), r.getSeverity(), r.isTriggered(),
                r.isEvaluationError(), r.getExplanation(),
                json.read(r.getSupportingObservations(), new TypeReference<List<SupportingObservation>>() {
                }), r.getRecommendedAction(), strings(r.getReferences()), r.isDemonstrationOnly());
    }

    private DifferentialView toDifferential(DifferentialDiagnosisRecord d, Map<UUID, ClinicianFeedback> latest,
                                            boolean finalized) {
        List<StatementView> statements = d.getEvidence().stream().map(this::toStatement).toList();
        ClinicianFeedback feedback = latest.get(d.getId());
        return new DifferentialView(d.getId(), d.getRank(), d.getRankBasis(), d.getConditionName(),
                d.getCodingSystem(), d.getConditionCode(), d.getCodeVerification(), d.getConfidenceLevel(),
                d.getOriginalConfidence(), d.getAssessmentMethod(), d.getGroundingStatus(), d.getWhyConsidered(),
                statements.stream().filter(s -> s.type() == EvidenceType.SUPPORTING).toList(),
                statements.stream().filter(s -> s.type() == EvidenceType.CONTRADICTING).toList(),
                statements.stream().filter(s -> s.type() == EvidenceType.MISSING_INFORMATION).toList(),
                d.getInvestigations().stream().map(i -> {
                    ClinicianFeedback f = latest.get(i.getId());
                    return new InvestigationSuggestionView(i.getId(), i.getName(), i.getReason(), i.getProposedBy(),
                            strings(i.getSupportingEvidenceKeys()), i.getSupportStatus(), reviewState(f, finalized),
                            toFeedback(f));
                }).toList(),
                d.getDistinguishingFeatures(), d.getReasoningSummary(), strings(d.getCitations()),
                strings(d.getValidationNotes()), reviewState(feedback, finalized), toFeedback(feedback));
    }

    private StatementView toStatement(DiagnosticEvidenceRecord e) {
        return new StatementView(e.getId(), e.getEvidenceType(), e.getStatement(), strings(e.getFactKeys()),
                strings(e.getEvidenceKeys()), e.getSupportStatus());
    }

    private static ReviewState reviewState(ClinicianFeedback feedback, boolean finalized) {
        return feedback == null ? ReviewState.AI_GENERATED
                : ReviewState.from(feedback.getAction(), feedback.getModifiedText() != null, finalized);
    }

    private static FeedbackView toFeedback(ClinicianFeedback f) {
        return f == null ? null : new FeedbackView(f.getAction(), f.getClinicianId(), f.getCreatedAt(), f.getReason(),
                f.getModifiedText());
    }

    private List<String> strings(String jsonArray) {
        return jsonArray == null ? List.of() : json.read(jsonArray, STRING_LIST);
    }
}
