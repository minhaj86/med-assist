package com.medassist.fhir.application;

import ca.uhn.fhir.context.FhirContext;
import com.medassist.allergy.application.AllergyService;
import com.medassist.assessment.application.AssessmentQueryService;
import com.medassist.assessment.application.AssessmentViews;
import com.medassist.assessment.domain.AssessmentWorkflowStatus;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.fhir.mapping.FhirResourceMapper;
import com.medassist.medication.application.MedicationService;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.application.VitalSignService;
import com.medassist.patient.application.PatientService;
import com.medassist.patient.domain.Patient;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exports MedAssist records as HL7 FHIR R4 JSON. Only clinician-finalized diagnoses are exported as Conditions. */
@Service
public class FhirExportService {

    private final FhirContext fhirContext;
    private final FhirResourceMapper mapper;
    private final PatientService patientService;
    private final EncounterService encounterService;
    private final VitalSignService vitalSignService;
    private final LabObservationService labService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final AssessmentQueryService assessmentQueryService;
    private final AuditService auditService;
    private final Clock clock;

    public FhirExportService(FhirContext fhirContext, FhirResourceMapper mapper, PatientService patientService,
                             EncounterService encounterService, VitalSignService vitalSignService,
                             LabObservationService labService, MedicationService medicationService,
                             AllergyService allergyService, AssessmentQueryService assessmentQueryService,
                             AuditService auditService, Clock clock) {
        this.fhirContext = fhirContext;
        this.mapper = mapper;
        this.patientService = patientService;
        this.encounterService = encounterService;
        this.vitalSignService = vitalSignService;
        this.labService = labService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.assessmentQueryService = assessmentQueryService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public String patient(UUID patientId) {
        Patient patient = patientService.require(patientId);
        auditService.record(AuditEntry.of(AuditEventType.FHIR_EXPORT).entity("Patient", patientId).patient(patientId)
                .with("resourceType", "Patient"));
        return encode(mapper.toFhir(patient));
    }

    @Transactional
    public String encounterBundle(UUID encounterId) {
        Encounter encounter = encounterService.require(encounterId);
        Patient patient = patientService.require(encounter.getPatientId());
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(Date.from(clock.instant()));
        add(bundle, mapper.toFhir(patient));
        add(bundle, mapper.toFhir(encounter));
        vitalSignService.list(encounterId, false).forEach(o -> add(bundle, mapper.toFhir(o)));
        labService.list(encounterId, false).forEach(l -> add(bundle, mapper.toFhir(l)));
        medicationService.listForPatient(patient.getId(), false).forEach(m -> add(bundle, mapper.toFhir(m)));
        allergyService.listForPatient(patient.getId(), false).forEach(a -> add(bundle, mapper.toFhir(a)));
        assessmentQueryService.finalAssessment(encounterId)
                .filter(f -> f.status() == AssessmentWorkflowStatus.FINALIZED)
                .ifPresent(f -> addConditions(bundle, f, patient.getId(), encounterId));
        auditService.record(AuditEntry.of(AuditEventType.FHIR_EXPORT)
                .entity("Encounter", encounterId)
                .patient(patient.getId())
                .encounter(encounterId)
                .with("resourceType", "Bundle")
                .with("entries", bundle.getEntry().size()));
        return encode(bundle);
    }

    private void addConditions(Bundle bundle, AssessmentViews.FinalAssessmentView f, UUID patientId, UUID encounterId) {
        f.diagnoses().forEach(d -> add(bundle, mapper.toFhir(d, patientId, encounterId, f.finalizedBy(),
                Date.from(f.finalizedAt()))));
    }

    private static void add(Bundle bundle, Resource resource) {
        bundle.addEntry().setResource(resource);
    }

    private String encode(org.hl7.fhir.instance.model.api.IBaseResource resource) {
        return fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
    }
}
