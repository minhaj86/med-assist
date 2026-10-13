package com.medassist.allergy.api;

import com.medassist.allergy.application.AllergyService;
import com.medassist.common.api.CommonDtos;
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
@Tag(name = "Clinical data - Allergies")
@RequireClinician
public class AllergyController {

    private final AllergyService service;
    private final AllergyMapper mapper;

    public AllergyController(AllergyService service, AllergyMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/api/encounters/{encounterId}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an allergy or intolerance")
    public AllergyDtos.AllergyResponse record(@PathVariable UUID encounterId,
                                              @Valid @RequestBody AllergyDtos.AllergyRequest r) {
        return mapper.toResponse(service.record(encounterId, r.allergen(), r.category(), r.reaction(), r.severity(),
                r.status(), r.source(), r.sourceDetail()));
    }

    @GetMapping("/api/patients/{patientId}/allergies")
    @Operation(summary = "List the patient's allergies")
    public List<AllergyDtos.AllergyResponse> list(@PathVariable UUID patientId,
                                                  @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.listForPatient(patientId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PatchMapping("/api/allergies/{allergyId}")
    @Operation(summary = "Change allergy status")
    public AllergyDtos.AllergyResponse changeStatus(@PathVariable UUID allergyId,
                                                    @Valid @RequestBody AllergyDtos.AllergyStatusRequest r) {
        return mapper.toResponse(service.changeStatus(allergyId, r.status()));
    }

    @PostMapping("/api/allergies/{allergyId}/entered-in-error")
    @Operation(summary = "Mark an allergy entry as entered in error")
    public AllergyDtos.AllergyResponse enteredInError(@PathVariable UUID allergyId,
                                                      @Valid @RequestBody CommonDtos.EnteredInErrorRequest r) {
        return mapper.toResponse(service.markEnteredInError(allergyId, r.reason()));
    }
}
