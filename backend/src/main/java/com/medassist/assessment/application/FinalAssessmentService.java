package com.medassist.assessment.application;

import com.medassist.assessment.domain.Assessment;
import com.medassist.assessment.domain.AssessmentRepository;
import com.medassist.assessment.domain.AssessmentVersion;
import com.medassist.assessment.domain.AssessmentVersionRepository;
import com.medassist.assessment.domain.FinalDiagnosis;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.security.CurrentUserProvider;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.TerminologyService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Documents the clinician's final assessment. Finalization is always an explicit, attested clinician action. */
@Service
public class FinalAssessmentService {

    public record DiagnosisInput(String conditionName, CodingSystem codingSystem, String code,
                                 FinalDiagnosis.DiagnosisCertainty certainty, UUID sourceSuggestionId) {
    }

    public record FinalizeCommand(UUID reviewedVersionId, String summary, String plan, List<DiagnosisInput> diagnoses,
                                  boolean attestation) {
    }

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository versionRepository;
    private final EncounterService encounterService;
    private final TerminologyService terminologyService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final JsonCodec json;
    private final Clock clock;

    public FinalAssessmentService(AssessmentRepository assessmentRepository,
                                  AssessmentVersionRepository versionRepository, EncounterService encounterService,
                                  TerminologyService terminologyService, AuditService auditService,
                                  CurrentUserProvider currentUser, JsonCodec json, Clock clock) {
        this.assessmentRepository = assessmentRepository;
        this.versionRepository = versionRepository;
        this.encounterService = encounterService;
        this.terminologyService = terminologyService;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public Assessment finalizeAssessment(UUID encounterId, FinalizeCommand command) {
        Encounter encounter = encounterService.require(encounterId);
        if (!command.attestation()) {
            throw new DomainRuleViolationException(
                    "Finalization requires the clinician to attest that the assessment reflects their own judgment");
        }
        Assessment assessment = assessmentRepository.findByEncounterId(encounterId)
                .orElseGet(() -> assessmentRepository.save(Assessment.open(encounterId, encounter.getPatientId())));
        if (command.reviewedVersionId() != null) {
            AssessmentVersion reviewed = versionRepository.findById(command.reviewedVersionId())
                    .orElseThrow(() -> NotFoundException.of("Assessment version", command.reviewedVersionId()));
            if (!reviewed.getEncounterId().equals(encounterId)) {
                throw new DomainRuleViolationException("The reviewed assessment version belongs to another encounter");
            }
        }
        List<FinalDiagnosis> diagnoses = command.diagnoses().stream().map(this::toDiagnosis).toList();
        assessment.finalizeAssessment(command.reviewedVersionId(), command.summary(), command.plan(),
                json.write(diagnoses), currentUser.currentUserId(), Instant.now(clock), true);
        auditService.record(AuditEntry.of(AuditEventType.FINAL_ASSESSMENT_DOCUMENTED)
                .entity("Assessment", assessment.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("reviewedVersionId", command.reviewedVersionId())
                .with("diagnosisCount", diagnoses.size())
                .with("fromSuggestions", diagnoses.stream().filter(d -> d.sourceSuggestionId() != null).count()));
        return assessment;
    }

    private FinalDiagnosis toDiagnosis(DiagnosisInput input) {
        ClinicalConcept concept = input.code() == null || input.code().isBlank()
                ? terminologyService.normalize(input.conditionName(), ConceptDomain.CONDITION)
                : terminologyService.verify(input.codingSystem(), input.code(), input.conditionName());
        return new FinalDiagnosis(concept.displayName(), concept.system(), concept.code(),
                concept.verificationStatus(), input.certainty(), input.sourceSuggestionId());
    }
}
