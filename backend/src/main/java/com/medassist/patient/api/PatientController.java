package com.medassist.patient.api;

import com.medassist.common.domain.DataProvenance;
import com.medassist.common.web.PageResponse;
import com.medassist.patient.application.PatientCommands;
import com.medassist.patient.application.PatientService;
import com.medassist.patient.domain.Patient;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients")
@RequireClinician
public class PatientController {

    private final PatientService patientService;
    private final PatientMapper mapper;

    public PatientController(PatientService patientService, PatientMapper mapper) {
        this.patientService = patientService;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Register a patient")
    public ResponseEntity<PatientDtos.PatientResponse> create(@Valid @RequestBody PatientDtos.CreatePatientRequest request) {
        Patient patient = patientService.register(new PatientCommands.RegisterPatient(request.externalPatientId(),
                request.firstName(), request.lastName(), request.dateOfBirth(), request.biologicalSex(),
                request.gender(), request.contactPhone(), false, DataProvenance.CLINICIAN));
        return ResponseEntity.created(URI.create("/api/patients/" + patient.getId()))
                .body(mapper.toResponse(patient, patientService.today()));
    }

    @GetMapping("/{patientId}")
    @Operation(summary = "Get a patient (access is audited)")
    public PatientDtos.PatientResponse get(@PathVariable UUID patientId) {
        return mapper.toResponse(patientService.view(patientId), patientService.today());
    }

    @PutMapping("/{patientId}")
    @Operation(summary = "Update patient demographics")
    public PatientDtos.PatientResponse update(@PathVariable UUID patientId,
                                              @Valid @RequestBody PatientDtos.UpdatePatientRequest request) {
        Patient patient = patientService.updateDemographics(patientId, new PatientCommands.UpdateDemographics(
                request.firstName(), request.lastName(), request.dateOfBirth(), request.biologicalSex(),
                request.gender(), request.contactPhone()));
        return mapper.toResponse(patient, patientService.today());
    }

    @GetMapping
    @Operation(summary = "Search patients by name or external identifier")
    public PageResponse<PatientDtos.PatientSummary> search(@RequestParam(required = false) String query,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        var today = patientService.today();
        return PageResponse.from(patientService.search(query, page, size), p -> mapper.toSummary(p, today));
    }
}
