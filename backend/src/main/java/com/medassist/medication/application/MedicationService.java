package com.medassist.medication.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.medication.domain.Medication;
import com.medassist.medication.domain.MedicationRepository;
import com.medassist.medication.domain.MedicationStatus;
import com.medassist.patient.application.PatientService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicationService {

    private final MedicationRepository repository;
    private final EncounterService encounterService;
    private final PatientService patientService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public MedicationService(MedicationRepository repository, EncounterService encounterService,
                             PatientService patientService, ClinicalDataRecorder recorder, AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.patientService = patientService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    @Transactional
    public Medication record(UUID encounterId, Medication.Details details, DataProvenance source, String sourceDetail) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        Medication medication = Medication.record(encounter.getPatientId(), encounterId, details,
                recorder.provenance(source, sourceDetail, null));
        repository.save(medication);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("Medication", medication.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("provenance", medication.getProvenance().getSource()));
        return medication;
    }

    @Transactional
    public Medication changeStatus(UUID medicationId, MedicationStatus status, LocalDate endDate) {
        Medication medication = require(medicationId);
        medication.changeStatus(status, endDate);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_MODIFIED)
                .entity("Medication", medicationId)
                .patient(medication.getPatientId())
                .with("fields", List.of("status", "endDate"))
                .with("status", status));
        return medication;
    }

    @Transactional
    public Medication markEnteredInError(UUID medicationId, String reason) {
        Medication medication = require(medicationId);
        medication.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("Medication", medicationId)
                .patient(medication.getPatientId()));
        return medication;
    }

    @Transactional(readOnly = true)
    public List<Medication> listForPatient(UUID patientId, boolean includeEnteredInError) {
        patientService.require(patientId);
        return repository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .filter(m -> includeEnteredInError || !m.isEnteredInError())
                .toList();
    }

    private Medication require(UUID medicationId) {
        return repository.findById(medicationId).orElseThrow(() -> NotFoundException.of("Medication", medicationId));
    }
}
