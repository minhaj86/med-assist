package com.medassist.encounter.api;

import com.medassist.common.domain.DataProvenance;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Encounters")
@RequireClinician
public class EncounterController {

    private final EncounterService encounterService;
    private final EncounterMapper mapper;

    public EncounterController(EncounterService encounterService, EncounterMapper mapper) {
        this.encounterService = encounterService;
        this.mapper = mapper;
    }

    @PostMapping("/api/patients/{patientId}/encounters")
    @Operation(summary = "Start an encounter for a patient")
    public ResponseEntity<EncounterDtos.EncounterResponse> create(@PathVariable UUID patientId,
                                                                  @Valid @RequestBody EncounterDtos.CreateEncounterRequest request) {
        Encounter encounter = encounterService.start(patientId, request.encounterType(), request.chiefComplaint(),
                request.startedAt(), DataProvenance.CLINICIAN);
        return ResponseEntity.created(URI.create("/api/encounters/" + encounter.getId()))
                .body(mapper.toResponse(encounter));
    }

    @GetMapping("/api/patients/{patientId}/encounters")
    @Operation(summary = "List a patient's encounters")
    public List<EncounterDtos.EncounterResponse> listForPatient(@PathVariable UUID patientId) {
        return encounterService.listForPatient(patientId).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/api/encounters/{encounterId}")
    @Operation(summary = "Get an encounter (access is audited)")
    public EncounterDtos.EncounterResponse get(@PathVariable UUID encounterId) {
        return mapper.toResponse(encounterService.view(encounterId));
    }

    @PatchMapping("/api/encounters/{encounterId}")
    @Operation(summary = "Update the chief complaint")
    public EncounterDtos.EncounterResponse update(@PathVariable UUID encounterId,
                                                  @Valid @RequestBody EncounterDtos.UpdateEncounterRequest request) {
        return mapper.toResponse(encounterService.updateChiefComplaint(encounterId, request.chiefComplaint()));
    }

    @PostMapping("/api/encounters/{encounterId}/complete")
    @Operation(summary = "Complete the encounter (no further clinical data can be added)")
    public EncounterDtos.EncounterResponse complete(@PathVariable UUID encounterId) {
        return mapper.toResponse(encounterService.complete(encounterId));
    }
}
