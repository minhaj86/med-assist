package com.medassist.assessment.application;

import com.medassist.assessment.domain.Assessment;
import com.medassist.assessment.domain.AssessmentRepository;
import com.medassist.assessment.domain.AssessmentRun;
import com.medassist.assessment.domain.AssessmentVersion;
import com.medassist.assessment.domain.AssessmentVersionRepository;
import com.medassist.assessment.domain.DiagnosticEvidenceRecord;
import com.medassist.assessment.domain.DifferentialDiagnosisRecord;
import com.medassist.assessment.domain.EvidenceType;
import com.medassist.assessment.domain.ProposedBy;
import com.medassist.assessment.domain.RetrievedEvidenceRecord;
import com.medassist.assessment.domain.RuleResultRecord;
import com.medassist.assessment.domain.SafetyAlertRecord;
import com.medassist.assessment.domain.ScoreResultRecord;
import com.medassist.assessment.domain.SuggestedInvestigationRecord;
import com.medassist.clinicalrules.application.DerivedSafetyAlert;
import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.engine.RuleEvaluationReport;
import com.medassist.common.domain.AlertOrigin;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.differential.AssessmentMethod;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.RankedDifferential;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import com.medassist.diagnosis.reasoning.ReasoningOutcome;
import com.medassist.diagnosis.reasoning.ValidatedReasoning;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persists a complete, immutable assessment version in a single transaction. */
@Component
public class AssessmentVersionWriter {

    public record VersionContent(AssessmentRun run, ClinicalContext context, List<String> dataQualityWarnings,
                                 RuleEvaluationReport rules, List<ScoreResult> scores,
                                 List<DerivedSafetyAlert> deterministicAlerts,
                                 List<RetrievedClinicalEvidence> evidence, ReasoningOutcome reasoning,
                                 List<RankedDifferential> differentials) {
    }

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final AssessmentChangeCalculator changeCalculator;
    private final JsonCodec json;
    private final Clock clock;

    public AssessmentVersionWriter(AssessmentRepository assessmentRepository,
                                   AssessmentVersionRepository versionRepository,
                                   AssessmentChangeCalculator changeCalculator, JsonCodec json, Clock clock) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.changeCalculator = changeCalculator;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public AssessmentVersion write(VersionContent c) {
        Assessment assessment = assessmentRepository.findById(c.run().getAssessmentId())
                .orElseThrow(() -> NotFoundException.of("Assessment", c.run().getAssessmentId()));
        Optional<AssessmentVersion> previous = versionRepository.findFirstByAssessmentIdOrderByVersionNumberDesc(
                assessment.getId());
        int versionNumber = assessment.allocateNextVersionNumber();

        ContextSnapshot snapshot = ContextSnapshot.of(c.context());
        String snapshotJson = json.write(snapshot);

        List<RuleResultRecord> ruleRecords = ruleRecords(c.rules());
        List<ScoreResultRecord> scoreRecords = new ArrayList<>();
        for (int i = 0; i < c.scores().size(); i++) {
            ScoreResult s = c.scores().get(i);
            scoreRecords.add(new ScoreResultRecord(s.scoreId(), s.version(), json.write(s), i));
        }
        List<SafetyAlertRecord> alertRecords = alertRecords(c.deterministicAlerts(), c.reasoning().reasoning());
        List<RetrievedEvidenceRecord> evidenceRecords = evidenceRecords(c.evidence());
        List<DifferentialDiagnosisRecord> differentialRecords = differentialRecords(c.differentials());

        AssessmentChanges changes = previous
                .map(p -> changeCalculator.compute(p.getVersionNumber(),
                        json.read(p.getInputSnapshot(), ContextSnapshot.class), snapshot, summarize(p),
                        summarize(alertRecords, differentialRecords, c.reasoning().status())))
                .orElseGet(AssessmentChanges::initial);

        ValidatedReasoning reasoning = c.reasoning().reasoning();
        AssessmentVersion version = AssessmentVersion.create(new AssessmentVersion.Header(assessment.getId(),
                        c.context().encounterId(), c.context().patientId(), versionNumber, c.run().getId(),
                        Instant.now(clock), c.run().getRequestedBy(), snapshotJson, sha256(snapshotJson),
                        json.write(changes), c.reasoning().status(), c.reasoning().statusMessage(),
                        c.reasoning().aiExecutionId(),
                        json.write(reasoning == null ? List.of() : reasoning.uncertainties()),
                        json.write(reasoning == null ? List.of() : reasoning.additionalQuestions()),
                        json.write(c.reasoning().validationIssues()), json.write(c.dataQualityWarnings()),
                        json.write(c.rules().executedRules()), json.write(c.rules().disabledRuleIds())),
                ruleRecords, scoreRecords, alertRecords, evidenceRecords, differentialRecords);
        return versionRepository.save(version);
    }

    private List<RuleResultRecord> ruleRecords(RuleEvaluationReport report) {
        Map<String, UUID> definitionIds = new LinkedHashMap<>();
        report.executedRules().forEach(r -> definitionIds.put(r.ruleId(), r.definitionId()));
        List<RuleResultRecord> records = new ArrayList<>();
        int order = 0;
        for (RuleResult r : report.results()) {
            records.add(new RuleResultRecord(definitionIds.get(r.ruleId()), r.ruleId(), r.ruleVersion(), r.ruleName(),
                    r.severity(), r.triggered(), r.evaluationError(), r.explanation(),
                    json.write(r.supportingObservations()), r.recommendedAction(), json.write(r.references()),
                    r.demonstrationOnly(), order++));
        }
        return records;
    }

    private List<SafetyAlertRecord> alertRecords(List<DerivedSafetyAlert> deterministic, ValidatedReasoning reasoning) {
        List<SafetyAlertRecord> records = new ArrayList<>();
        int order = 0;
        for (DerivedSafetyAlert a : deterministic) {
            records.add(new SafetyAlertRecord(a.origin(), a.severity(), a.title(), a.explanation(),
                    a.recommendedAction(), a.sourceReference(), json.write(a.supportingFactKeys()), json.write(List.of()),
                    SupportStatus.NOT_APPLICABLE, a.demonstrationOnly(), order++));
        }
        if (reasoning != null) {
            for (ValidatedReasoning.RedFlag f : reasoning.redFlags()) {
                records.add(new SafetyAlertRecord(AlertOrigin.AI, f.severity(), f.title(), f.reason(),
                        "AI-generated consideration - verify against clinical findings.", null,
                        json.write(f.factKeys()), json.write(f.evidenceKeys()), f.support(), false, order++));
            }
        }
        return records;
    }

    private List<RetrievedEvidenceRecord> evidenceRecords(List<RetrievedClinicalEvidence> evidence) {
        List<RetrievedEvidenceRecord> records = new ArrayList<>();
        int order = 0;
        for (RetrievedClinicalEvidence e : evidence) {
            records.add(new RetrievedEvidenceRecord(e.evidenceKey(), e.chunkId(), e.documentId(), e.documentTitle(),
                    e.documentVersion(), e.sourceOrganization(), e.section(), e.page(), e.publicationDate(),
                    BigDecimal.valueOf(e.similarity()).setScale(5, RoundingMode.HALF_UP), e.text(), e.syntheticSource(),
                    order++));
        }
        return records;
    }

    private List<DifferentialDiagnosisRecord> differentialRecords(List<RankedDifferential> ranked) {
        List<DifferentialDiagnosisRecord> records = new ArrayList<>();
        for (RankedDifferential r : ranked) {
            if (r.ai() != null) {
                ValidatedReasoning.Differential d = r.ai();
                List<DiagnosticEvidenceRecord> evidence = new ArrayList<>();
                int order = 0;
                for (ValidatedReasoning.Statement s : d.supportingEvidence()) {
                    evidence.add(statement(EvidenceType.SUPPORTING, s, order++));
                }
                for (ValidatedReasoning.Statement s : d.contradictingEvidence()) {
                    evidence.add(statement(EvidenceType.CONTRADICTING, s, order++));
                }
                for (String missing : d.missingInformation()) {
                    evidence.add(new DiagnosticEvidenceRecord(EvidenceType.MISSING_INFORMATION, missing,
                            json.write(List.of()), json.write(List.of()), SupportStatus.NOT_APPLICABLE, order++));
                }
                List<SuggestedInvestigationRecord> investigations = new ArrayList<>();
                int invOrder = 0;
                for (ValidatedReasoning.Investigation i : d.suggestedInvestigations()) {
                    investigations.add(new SuggestedInvestigationRecord(i.name(), i.reason(), ProposedBy.AI,
                            json.write(i.evidenceKeys()), i.support(), invOrder++));
                }
                records.add(new DifferentialDiagnosisRecord(new DifferentialDiagnosisRecord.Values(r.rank(),
                        d.conditionName(), d.codingSystem(), d.code(), d.codeVerification(), d.confidence(),
                        d.originalConfidence(), AssessmentMethod.AI_REASONED, d.grounding(), d.whyConsidered(),
                        d.distinguishingFeatures(), d.reasoningSummary(), r.rankBasis(), json.write(d.citations()),
                        json.write(d.validationNotes())), evidence, investigations));
            } else {
                var c = r.knowledgeLinked();
                records.add(new DifferentialDiagnosisRecord(new DifferentialDiagnosisRecord.Values(r.rank(),
                        c.conditionName(), null, null, ConceptVerificationStatus.UNVERIFIED, null, null,
                        AssessmentMethod.KNOWLEDGE_LINKED, GroundingStatus.GROUNDED,
                        "Discussed in retrieved knowledge passages " + String.join(", ", c.evidenceKeys())
                                + " that match the recorded findings. Not assessed by AI; clinical evaluation is required.",
                        null, null, r.rankBasis(), json.write(c.evidenceKeys()), json.write(List.of())),
                        List.of(), List.of()));
            }
        }
        return records;
    }

    private DiagnosticEvidenceRecord statement(EvidenceType type, ValidatedReasoning.Statement s, int order) {
        return new DiagnosticEvidenceRecord(type, s.text(), json.write(s.factKeys()), json.write(s.evidenceKeys()),
                s.support(), order);
    }

    private static AssessmentChangeCalculator.OutputSummary summarize(AssessmentVersion v) {
        return summarize(v.getSafetyAlerts(), v.getDifferentials(), v.getAiStatus());
    }

    private static AssessmentChangeCalculator.OutputSummary summarize(List<SafetyAlertRecord> alerts,
                                                                      List<DifferentialDiagnosisRecord> differentials,
                                                                      AiReasoningStatus status) {
        Map<String, String> confidence = new LinkedHashMap<>();
        differentials.forEach(d -> confidence.put(d.getConditionName(),
                d.getConfidenceLevel() == null ? null : d.getConfidenceLevel().name()));
        return new AssessmentChangeCalculator.OutputSummary(
                alerts.stream().map(a -> "[" + a.getOrigin() + "] " + a.getTitle()).toList(), confidence, status);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
