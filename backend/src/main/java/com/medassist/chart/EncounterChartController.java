package com.medassist.chart;

import com.medassist.allergy.api.AllergyDtos;
import com.medassist.allergy.api.AllergyMapper;
import com.medassist.allergy.application.AllergyService;
import com.medassist.encounter.api.EncounterDtos;
import com.medassist.encounter.api.EncounterMapper;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.history.api.HistoryDtos;
import com.medassist.history.api.HistoryMapper;
import com.medassist.history.application.MedicalHistoryService;
import com.medassist.medication.api.MedicationDtos;
import com.medassist.medication.api.MedicationMapper;
import com.medassist.medication.application.MedicationService;
import com.medassist.observation.api.ObservationDtos;
import com.medassist.observation.api.ObservationMapper;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.application.VitalSignService;
import com.medassist.patient.api.PatientDtos;
import com.medassist.patient.api.PatientMapper;
import com.medassist.patient.application.PatientService;
import com.medassist.security.annotation.RequireClinician;
import com.medassist.symptom.api.SymptomDtos;
import com.medassist.symptom.api.SymptomMapper;
import com.medassist.symptom.application.SymptomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read model for the clinician workstation: the encounter with all clinical data in one response. Lives in its
 * own module so the clinical data modules do not depend on each other.
 */
@RestController
@Tag(name = "Encounters")
@RequireClinician
public class EncounterChartController {

    public record EncounterChartResponse(EncounterDtos.EncounterResponse encounter,
                                         PatientDtos.PatientResponse patient,
                                         List<SymptomDtos.SymptomResponse> symptoms,
                                         List<ObservationDtos.ObservationResponse> vitalSigns,
                                         List<ObservationDtos.LabResponse> labs,
                                         List<MedicationDtos.MedicationResponse> medications,
                                         List<AllergyDtos.AllergyResponse> allergies,
                                         List<HistoryDtos.HistoryResponse> history) {
    }

    private final EncounterService encounterService;
    private final PatientService patientService;
    private final SymptomService symptomService;
    private final VitalSignService vitalSignService;
    private final LabObservationService labService;
    private final MedicationService medicationService;
    private final AllergyService allergyService;
    private final MedicalHistoryService historyService;
    private final EncounterMapper encounterMapper;
    private final PatientMapper patientMapper;
    private final SymptomMapper symptomMapper;
    private final ObservationMapper observationMapper;
    private final MedicationMapper medicationMapper;
    private final AllergyMapper allergyMapper;
    private final HistoryMapper historyMapper;

    public EncounterChartController(EncounterService encounterService, PatientService patientService,
                                    SymptomService symptomService, VitalSignService vitalSignService,
                                    LabObservationService labService, MedicationService medicationService,
                                    AllergyService allergyService, MedicalHistoryService historyService,
                                    EncounterMapper encounterMapper, PatientMapper patientMapper,
                                    SymptomMapper symptomMapper, ObservationMapper observationMapper,
                                    MedicationMapper medicationMapper, AllergyMapper allergyMapper,
                                    HistoryMapper historyMapper) {
        this.encounterService = encounterService;
        this.patientService = patientService;
        this.symptomService = symptomService;
        this.vitalSignService = vitalSignService;
        this.labService = labService;
        this.medicationService = medicationService;
        this.allergyService = allergyService;
        this.historyService = historyService;
        this.encounterMapper = encounterMapper;
        this.patientMapper = patientMapper;
        this.symptomMapper = symptomMapper;
        this.observationMapper = observationMapper;
        this.medicationMapper = medicationMapper;
        this.allergyMapper = allergyMapper;
        this.historyMapper = historyMapper;
    }

    @GetMapping("/api/encounters/{encounterId}/chart")
    @Operation(summary = "Encounter chart: patient, symptoms, vitals, labs, medications, allergies and history")
    public EncounterChartResponse chart(@PathVariable UUID encounterId) {
        Encounter encounter = encounterService.view(encounterId);
        UUID patientId = encounter.getPatientId();
        var today = patientService.today();
        return new EncounterChartResponse(
                encounterMapper.toResponse(encounter),
                patientMapper.toResponse(patientService.require(patientId), today),
                symptomService.list(encounterId, true).stream().map(symptomMapper::toResponse).toList(),
                vitalSignService.list(encounterId, true).stream().map(observationMapper::toResponse).toList(),
                labService.list(encounterId, true).stream().map(observationMapper::toResponse).toList(),
                medicationService.listForPatient(patientId, true).stream().map(medicationMapper::toResponse).toList(),
                allergyService.listForPatient(patientId, true).stream().map(allergyMapper::toResponse).toList(),
                historyService.listForPatient(patientId, true).stream().map(historyMapper::toResponse).toList());
    }
}
