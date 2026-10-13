package com.medassist.medication.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.medication.application.MedicationService;
import com.medassist.medication.domain.Medication;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Clinical data - Medications")
@RequireClinician
public class MedicationController {

    private final MedicationService service;
    private final MedicationMapper mapper;

    public MedicationController(MedicationService service, MedicationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/api/encounters/{encounterId}/medications")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a medication to the patient's medication list")
    public MedicationDtos.MedicationResponse record(@PathVariable UUID encounterId,
                                                    @Valid @RequestBody MedicationDtos.MedicationRequest r) {
        var details = new Medication.Details(r.medicationName(), r.codingSystem(), r.code(), r.dosage(), r.frequency(),
                r.route(), r.startDate(), r.endDate(), r.status());
        return mapper.toResponse(service.record(encounterId, details, r.source(), r.sourceDetail()));
    }

    @GetMapping("/api/patients/{patientId}/medications")
    @Operation(summary = "List the patient's medications")
    public List<MedicationDtos.MedicationResponse> list(@PathVariable UUID patientId,
                                                        @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.listForPatient(patientId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PatchMapping("/api/medications/{medicationId}")
    @Operation(summary = "Change medication status")
    public MedicationDtos.MedicationResponse changeStatus(@PathVariable UUID medicationId,
                                                          @Valid @RequestBody MedicationDtos.MedicationStatusRequest r) {
        return mapper.toResponse(service.changeStatus(medicationId, r.status(), r.endDate()));
    }

    @PostMapping("/api/medications/{medicationId}/entered-in-error")
    @Operation(summary = "Mark a medication entry as entered in error")
    public MedicationDtos.MedicationResponse enteredInError(@PathVariable UUID medicationId,
                                                            @Valid @RequestBody CommonDtos.EnteredInErrorRequest r) {
        return mapper.toResponse(service.markEnteredInError(medicationId, r.reason()));
    }
}
