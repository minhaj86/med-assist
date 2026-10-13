package com.medassist.observation.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.observation.domain.LabObservation;
import com.medassist.observation.domain.LabObservationRepository;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.TerminologyService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabObservationService {

    private final LabObservationRepository repository;
    private final EncounterService encounterService;
    private final TerminologyService terminologyService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public LabObservationService(LabObservationRepository repository, EncounterService encounterService,
                                 TerminologyService terminologyService, ClinicalDataRecorder recorder,
                                 AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.terminologyService = terminologyService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    @Transactional
    public LabObservation record(UUID encounterId, String testName, CodingSystem codingSystem, String testCode,
                                 LabObservation.Result result, DataProvenance source, String sourceDetail) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        if (result.collectedAt() != null && result.collectedAt().isAfter(recorder.now().plus(Duration.ofMinutes(5)))) {
            throw new DomainRuleViolationException("Collection time cannot be in the future");
        }
        ClinicalConcept test = testCode == null || testCode.isBlank()
                ? terminologyService.normalize(testName, ConceptDomain.LAB_TEST)
                : terminologyService.verify(codingSystem == null ? CodingSystem.LOINC : codingSystem, testCode,
                terminologyService.normalize(testName, ConceptDomain.LAB_TEST).displayName());
        LabObservation lab = LabObservation.record(encounterId, encounter.getPatientId(), test, result,
                recorder.provenance(source == null ? DataProvenance.CLINICIAN : source, sourceDetail, null));
        repository.save(lab);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("LabObservation", lab.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("provenance", lab.getProvenance().getSource())
                .with("codeVerification", test.verificationStatus())
                .with("abnormalFlagSource", lab.getAbnormalFlagSource()));
        return lab;
    }

    @Transactional
    public LabObservation markEnteredInError(UUID encounterId, UUID labId, String reason) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        LabObservation lab = repository.findById(labId)
                .filter(l -> l.getEncounterId().equals(encounterId))
                .orElseThrow(() -> NotFoundException.of("LabObservation", labId));
        lab.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("LabObservation", labId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return lab;
    }

    @Transactional(readOnly = true)
    public List<LabObservation> list(UUID encounterId, boolean includeEnteredInError) {
        encounterService.require(encounterId);
        return repository.findByEncounterIdOrderByCollectedAtDesc(encounterId).stream()
                .filter(l -> includeEnteredInError || !l.isEnteredInError())
                .toList();
    }
}
