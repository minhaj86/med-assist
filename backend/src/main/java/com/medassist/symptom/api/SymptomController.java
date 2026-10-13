package com.medassist.symptom.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.security.annotation.RequireClinician;
import com.medassist.symptom.application.SymptomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/encounters/{encounterId}/symptoms")
@Tag(name = "Clinical data - Symptoms")
@RequireClinician
public class SymptomController {

    private final SymptomService service;
    private final SymptomMapper mapper;

    public SymptomController(SymptomService service, SymptomMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a symptom or pertinent negative")
    public SymptomDtos.SymptomResponse create(@PathVariable UUID encounterId,
                                              @Valid @RequestBody SymptomDtos.SymptomRequest request) {
        return mapper.toResponse(service.record(encounterId, request.symptom(), mapper.toDetails(request),
                request.source(), request.sourceDetail(), request.aiProposalReference()));
    }

    @GetMapping
    @Operation(summary = "List symptoms recorded in the encounter")
    public List<SymptomDtos.SymptomResponse> list(@PathVariable UUID encounterId,
                                                  @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.list(encounterId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PutMapping("/{symptomId}")
    @Operation(summary = "Revise a symptom")
    public SymptomDtos.SymptomResponse update(@PathVariable UUID encounterId, @PathVariable UUID symptomId,
                                              @Valid @RequestBody SymptomDtos.SymptomRequest request) {
        return mapper.toResponse(service.revise(encounterId, symptomId, request.symptom(), mapper.toDetails(request)));
    }

    @PostMapping("/{symptomId}/entered-in-error")
    @Operation(summary = "Mark a symptom as entered in error (records are never deleted)")
    public SymptomDtos.SymptomResponse enteredInError(@PathVariable UUID encounterId, @PathVariable UUID symptomId,
                                                      @Valid @RequestBody CommonDtos.EnteredInErrorRequest request) {
        return mapper.toResponse(service.markEnteredInError(encounterId, symptomId, request.reason()));
    }
}
