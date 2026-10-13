package com.medassist.assessment.application;

import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentStage;
import com.medassist.assessment.domain.AssessmentVersion;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.clinicalrules.application.DerivedSafetyAlert;
import com.medassist.clinicalrules.application.SafetyAlertDerivation;
import com.medassist.clinicalrules.engine.ClinicalRuleEngine;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.common.web.CorrelationId;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContextAssembler;
import com.medassist.diagnosis.differential.DifferentialCandidate;
import com.medassist.diagnosis.differential.DifferentialRanker;
import com.medassist.diagnosis.differential.KnowledgeLinkedCandidateGenerator;
import com.medassist.diagnosis.differential.RankedDifferential;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import com.medassist.diagnosis.reasoning.ClinicalReasoningPort;
import com.medassist.diagnosis.reasoning.ReasoningInput;
import com.medassist.diagnosis.reasoning.ReasoningOutcome;
import com.medassist.diagnosis.scoring.ClinicalScoringService;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.knowledge.retrieval.ClinicalKnowledgeRetriever;
import com.medassist.knowledge.retrieval.KnowledgeQuery;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The clinical assessment pipeline:
 * validation -> terminology summary -> deterministic rules -> scores -> knowledge retrieval -> candidate generation
 * -> AI reasoning (optional) -> structured/citation/safety validation (inside the reasoning port) -> ranking -> an
 * immutable version.
 *
 * <p>Deterministic stages always complete; knowledge or AI failures degrade the result but never block it.
 */
@Component
public class AssessmentPipeline {

    private static final Logger log = LoggerFactory.getLogger(AssessmentPipeline.class);
    static final int MAX_EVIDENCE = 6;
    static final Duration STALE_VITALS = Duration.ofHours(6);

    private final AssessmentRunTracker tracker;
    private final ClinicalContextAssembler assembler;
    private final ClinicalRuleEngine ruleEngine;
    private final ClinicalScoringService scoringService;
    private final SafetyAlertDerivation alertDerivation;
    private final ObjectProvider<ClinicalKnowledgeRetriever> retrieverProvider;
    private final KnowledgeLinkedCandidateGenerator candidateGenerator;
    private final ObjectProvider<ClinicalReasoningPort> reasoningProvider;
    private final DifferentialRanker ranker;
    private final AssessmentVersionWriter writer;
    private final AuditService auditService;
    private final Timer assessmentTimer;
    private final Clock clock;

    public AssessmentPipeline(AssessmentRunTracker tracker, ClinicalContextAssembler assembler,
                              ClinicalRuleEngine ruleEngine, ClinicalScoringService scoringService,
                              SafetyAlertDerivation alertDerivation,
                              ObjectProvider<ClinicalKnowledgeRetriever> retrieverProvider,
                              KnowledgeLinkedCandidateGenerator candidateGenerator,
                              ObjectProvider<ClinicalReasoningPort> reasoningProvider, DifferentialRanker ranker,
                              AssessmentVersionWriter writer, AuditService auditService, MeterRegistry meterRegistry,
                              Clock clock) {
        this.tracker = tracker;
        this.assembler = assembler;
        this.ruleEngine = ruleEngine;
        this.scoringService = scoringService;
        this.alertDerivation = alertDerivation;
        this.retrieverProvider = retrieverProvider;
        this.candidateGenerator = candidateGenerator;
        this.reasoningProvider = reasoningProvider;
        this.ranker = ranker;
        this.writer = writer;
        this.auditService = auditService;
        this.assessmentTimer = Timer.builder("assessment_duration")
                .description("End-to-end duration of the clinical assessment pipeline")
                .register(meterRegistry);
        this.clock = clock;
    }

    public void execute(UUID runId) {
        long start = System.nanoTime();
        AssessmentRun run = tracker.advance(runId, AssessmentStage.VALIDATING_INPUT);
        if (run.getCorrelationId() != null) {
            MDC.put(CorrelationId.MDC_KEY, run.getCorrelationId());
        }
        try {
            ClinicalContext context = assembler.assemble(run.getEncounterId());
            List<String> dataQuality = dataQualityWarnings(context);

            tracker.advance(runId, AssessmentStage.NORMALIZING_TERMINOLOGY);
            long uncoded = context.symptoms().stream()
                    .filter(s -> s.conceptVerification() == ConceptVerificationStatus.UNVERIFIED).count();
            if (uncoded > 0) {
                dataQuality.add(uncoded + " symptom concept(s) have no verified terminology code; display names are used.");
            }

            tracker.advance(runId, AssessmentStage.RUNNING_RULES);
            RuleEvaluationReport rules = ruleEngine.evaluate(context);

            tracker.advance(runId, AssessmentStage.CALCULATING_SCORES);
            List<ScoreResult> scores = scoringService.calculateAll(context);
            List<DerivedSafetyAlert> alerts = alertDerivation.derive(rules, scores);

            tracker.advance(runId, AssessmentStage.SEARCHING_KNOWLEDGE);
            List<RetrievedClinicalEvidence> evidence = retrieveEvidence(context, dataQuality);

            tracker.advance(runId, AssessmentStage.GENERATING_CANDIDATES);
            List<DifferentialCandidate> candidates = candidateGenerator.generate(evidence);

            ReasoningOutcome outcome = reason(run, context, rules, scores, evidence, candidates);
            List<RankedDifferential> differentials = ranker.rank(outcome.reasoning(), candidates);

            tracker.advance(runId, AssessmentStage.SAVING);
            AssessmentVersion version = writer.write(new AssessmentVersionWriter.VersionContent(run, context,
                    dataQuality, rules, scores, alerts, evidence, outcome, differentials));
            tracker.complete(runId, version.getId());

            auditService.record(AuditEntry.of(AuditEventType.ASSESSMENT_COMPLETED)
                    .actor(run.getRequestedBy())
                    .entity("AssessmentVersion", version.getId())
                    .patient(run.getPatientId())
                    .encounter(run.getEncounterId())
                    .with("runId", runId)
                    .with("versionNumber", version.getVersionNumber())
                    .with("inputHash", version.getInputHash())
                    .with("rulesExecuted", rules.executedRules().stream().map(r -> r.ruleId() + "@" + r.version()).toList())
                    .with("rulesTriggered", rules.triggered().stream().map(r -> r.ruleId() + "@" + r.ruleVersion()).toList())
                    .with("scores", scores.stream().map(s -> s.scoreId() + "@" + s.version()).toList())
                    .with("evidence", evidence.stream().map(e -> e.documentId() + "@" + e.documentVersion() + "#" + e.chunkId()).toList())
                    .with("aiStatus", outcome.status())
                    .with("aiExecutionId", outcome.aiExecutionId())
                    .with("differentialCount", differentials.size()));
        } catch (RuntimeException e) {
            log.error("Assessment run {} failed", runId, e);
            tracker.fail(runId, "The assessment could not be completed. Clinical data is unchanged; please retry.");
            auditService.recordIndependently(AuditEntry.of(AuditEventType.ASSESSMENT_FAILED)
                    .actor(run.getRequestedBy())
                    .entity("AssessmentRun", runId)
                    .patient(run.getPatientId())
                    .encounter(run.getEncounterId())
                    .with("errorType", e.getClass().getSimpleName())
                    .failure());
        } finally {
            assessmentTimer.record(Duration.ofNanos(System.nanoTime() - start));
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }

    private ReasoningOutcome reason(AssessmentRun run, ClinicalContext context, RuleEvaluationReport rules,
                                    List<ScoreResult> scores, List<RetrievedClinicalEvidence> evidence,
                                    List<DifferentialCandidate> candidates) {
        if (!run.isIncludeAi()) {
            return ReasoningOutcome.without(AiReasoningStatus.NOT_REQUESTED,
                    "Assessment run without AI reasoning at the clinician's request.");
        }
        ClinicalReasoningPort port = reasoningProvider.getIfAvailable();
        if (port == null) {
            return ReasoningOutcome.without(AiReasoningStatus.NOT_CONFIGURED,
                    "AI reasoning is not available in this installation. Deterministic results are shown.");
        }
        tracker.advance(run.getId(), AssessmentStage.GENERATING_REASONING);
        ReasoningOutcome outcome = port.reason(new ReasoningInput(run.getId(), run.getRequestedBy(), context, rules,
                scores, evidence, candidates));
        tracker.advance(run.getId(), AssessmentStage.VALIDATING_RESPONSE);
        return outcome;
    }

    private List<RetrievedClinicalEvidence> retrieveEvidence(ClinicalContext context, List<String> dataQuality) {
        ClinicalKnowledgeRetriever retriever = retrieverProvider.getIfAvailable();
        if (retriever == null) {
            dataQuality.add("Knowledge base is not available; no knowledge references were retrieved.");
            return List.of();
        }
        try {
            List<RetrievedClinicalEvidence> evidence = retriever.retrieve(buildQuery(context));
            if (evidence.isEmpty()) {
                dataQuality.add("No approved knowledge-base passages matched this presentation.");
            }
            return evidence;
        } catch (RuntimeException e) {
            log.warn("Knowledge retrieval failed; continuing without knowledge references", e);
            dataQuality.add("Knowledge retrieval failed; assessment continued without knowledge references.");
            return List.of();
        }
    }

    static KnowledgeQuery buildQuery(ClinicalContext context) {
        List<String> symptoms = new ArrayList<>();
        if (context.chiefComplaint() != null) {
            symptoms.add(context.chiefComplaint());
        }
        context.presentSymptoms().forEach(s -> symptoms.add(s.displayName()
                + (s.character() == null ? "" : " " + s.character())
                + (s.location() == null ? "" : " " + s.location())));
        List<String> observations = new ArrayList<>();
        context.labs().stream()
                .filter(l -> l.abnormalFlag() != null && l.abnormalFlag() != AbnormalFlag.NORMAL)
                .forEach(l -> observations.add(l.testName() + " " + l.abnormalFlag().name().toLowerCase().replace('_', ' ')));
        return new KnowledgeQuery(symptoms, observations, List.of(), null, MAX_EVIDENCE);
    }

    private List<String> dataQualityWarnings(ClinicalContext context) {
        List<String> warnings = new ArrayList<>();
        if (context.chiefComplaint() == null && context.presentSymptoms().isEmpty()) {
            warnings.add("No presenting complaint or symptoms recorded - the assessment will be limited.");
        }
        if (context.vitalSigns().isEmpty()) {
            warnings.add("No vital signs recorded - vital-sign safety rules could not be evaluated.");
        } else {
            Instant newest = context.vitalSigns().stream().map(v -> v.measuredAt()).max(Instant::compareTo).orElseThrow();
            if (Duration.between(newest, Instant.now(clock)).compareTo(STALE_VITALS) > 0) {
                warnings.add("Most recent vital signs are more than 6 hours old.");
            }
        }
        return warnings;
    }
}
