package com.medassist.allergy.application;

import com.medassist.allergy.domain.Allergy;
import com.medassist.allergy.domain.AllergyCategory;
import com.medassist.allergy.domain.AllergyRepository;
import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.allergy.domain.AllergyStatus;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.patient.application.PatientService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllergyService {

    private final AllergyRepository repository;
    private final EncounterService encounterService;
    private final PatientService patientService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public AllergyService(AllergyRepository repository, EncounterService encounterService,
                          PatientService patientService, ClinicalDataRecorder recorder, AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.patientService = patientService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    @Transactional
    public Allergy record(UUID encounterId, String allergen, AllergyCategory category, String reaction,
                          AllergySeverity severity, AllergyStatus status, DataProvenance source, String sourceDetail) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        Allergy allergy = Allergy.record(encounter.getPatientId(), encounterId, allergen, category, reaction, severity,
                status, recorder.provenance(source, sourceDetail, null));
        repository.save(allergy);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("Allergy", allergy.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("provenance", allergy.getProvenance().getSource()));
        return allergy;
    }

    @Transactional
    public Allergy changeStatus(UUID allergyId, AllergyStatus status) {
        Allergy allergy = require(allergyId);
        allergy.changeStatus(status);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_MODIFIED)
                .entity("Allergy", allergyId)
                .patient(allergy.getPatientId())
                .with("fields", List.of("status"))
                .with("status", status));
        return allergy;
    }

    @Transactional
    public Allergy markEnteredInError(UUID allergyId, String reason) {
        Allergy allergy = require(allergyId);
        allergy.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("Allergy", allergyId)
                .patient(allergy.getPatientId()));
        return allergy;
    }

    @Transactional(readOnly = true)
    public List<Allergy> listForPatient(UUID patientId, boolean includeEnteredInError) {
        patientService.require(patientId);
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(a -> includeEnteredInError || !a.isEnteredInError())
                .toList();
    }

    private Allergy require(UUID allergyId) {
        return repository.findById(allergyId).orElseThrow(() -> NotFoundException.of("Allergy", allergyId));
    }
}
