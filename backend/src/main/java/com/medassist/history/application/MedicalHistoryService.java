package com.medassist.history.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.domain.ClinicalDataRecorder;
import com.medassist.common.domain.DataProvenance;
import com.medassist.common.error.NotFoundException;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.MedicalHistoryEntry;
import com.medassist.history.domain.MedicalHistoryRepository;
import com.medassist.patient.application.PatientService;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.TerminologyService;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalHistoryService {

    private static final Set<HistoryCategory> CONDITION_CATEGORIES =
            EnumSet.of(HistoryCategory.PAST_DIAGNOSIS, HistoryCategory.FAMILY_HISTORY);

    private final MedicalHistoryRepository repository;
    private final EncounterService encounterService;
    private final PatientService patientService;
    private final TerminologyService terminologyService;
    private final ClinicalDataRecorder recorder;
    private final AuditService auditService;

    public MedicalHistoryService(MedicalHistoryRepository repository, EncounterService encounterService,
                                 PatientService patientService, TerminologyService terminologyService,
                                 ClinicalDataRecorder recorder, AuditService auditService) {
        this.repository = repository;
        this.encounterService = encounterService;
        this.patientService = patientService;
        this.terminologyService = terminologyService;
        this.recorder = recorder;
        this.auditService = auditService;
    }

    @Transactional
    public MedicalHistoryEntry record(UUID encounterId, MedicalHistoryEntry.Details details, DataProvenance source,
                                      String sourceDetail, String aiProposalReference) {
        Encounter encounter = encounterService.requireModifiable(encounterId);
        ClinicalConcept concept = CONDITION_CATEGORIES.contains(details.category())
                ? terminologyService.normalize(details.description(), ConceptDomain.CONDITION)
                : null;
        MedicalHistoryEntry entry = MedicalHistoryEntry.record(encounter.getPatientId(), encounterId, concept, details,
                recorder.provenance(source, sourceDetail, aiProposalReference));
        repository.save(entry);
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_CREATED)
                .entity("MedicalHistoryEntry", entry.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("category", details.category())
                .with("provenance", entry.getProvenance().getSource()));
        return entry;
    }

    @Transactional
    public MedicalHistoryEntry markEnteredInError(UUID entryId, String reason) {
        MedicalHistoryEntry entry = repository.findById(entryId)
                .orElseThrow(() -> NotFoundException.of("MedicalHistoryEntry", entryId));
        entry.markEnteredInError(reason, recorder.currentUserId(), recorder.now());
        auditService.record(AuditEntry.of(AuditEventType.CLINICAL_DATA_ENTERED_IN_ERROR)
                .entity("MedicalHistoryEntry", entryId)
                .patient(entry.getPatientId()));
        return entry;
    }

    @Transactional(readOnly = true)
    public List<MedicalHistoryEntry> listForPatient(UUID patientId, boolean includeEnteredInError) {
        patientService.require(patientId);
        return repository.findByPatientIdOrderByCategoryAscCreatedAtDesc(patientId).stream()
                .filter(h -> includeEnteredInError || !h.isEnteredInError())
                .toList();
    }
}
