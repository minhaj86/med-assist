package com.medassist.symptom.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.symptom.domain.Symptom;
import com.medassist.symptom.domain.SymptomRepository;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.TerminologyService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SymptomService {

    private final SymptomRepository repository;
    private final EncounterService encounterService;
    private final TerminologyService terminologyService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public SymptomService(SymptomRepository repository, EncounterService encounterService,
                          TerminologyService terminologyService, ClinicalDataRecorder recorder,
                          AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.terminologyService = terminologyService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    @Transactional
    public Symptom record(UUID encounterId, String symptomText, Symptom.Details details, DataProvenance source,
                          String sourceDetail, String aiProposalReference) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        ClinicalConcept concept = terminologyService.normalize(symptomText, ConceptDomain.SYMPTOM);
        Symptom symptom = Symptom.record(encounterId, encounter.getPatientId(), concept, details,
                recorder.provenance(source, sourceDetail, aiProposalReference));
        repository.save(symptom);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("Symptom", symptom.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("provenance", symptom.getProvenance().getSource())
                .with("conceptVerification", concept.verificationStatus()));
        return symptom;
    }

    @Transactional
    public Symptom revise(UUID encounterId, UUID symptomId, String symptomText, Symptom.Details details) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        Symptom symptom = require(encounterId, symptomId);
        symptom.revise(terminologyService.normalize(symptomText, ConceptDomain.SYMPTOM), details);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_MODIFIED)
                .entity("Symptom", symptomId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return symptom;
    }

    @Transactional
    public Symptom markEnteredInError(UUID encounterId, UUID symptomId, String reason) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        Symptom symptom = require(encounterId, symptomId);
        symptom.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("Symptom", symptomId)
                .patient(encounter.getPatientId())
                .encounter(encounterId));
        return symptom;
    }

    @Transactional(readOnly = true)
    public List<Symptom> list(UUID encounterId, boolean includeEnteredInError) {
        encounterService.require(encounterId);
        return repository.findByEncounterIdOrderByCreatedAtAsc(encounterId).stream()
                .filter(s -> includeEnteredInError || !s.isEnteredInError())
                .toList();
    }

    private Symptom require(UUID encounterId, UUID symptomId) {
        return repository.findById(symptomId)
                .filter(s -> s.getEncounterId().equals(encounterId))
                .orElseThrow(() -> NotFoundException.of("Symptom", symptomId));
    }
}
