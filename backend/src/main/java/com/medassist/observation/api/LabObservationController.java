package com.medassist.observation.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.observation.application.LabObservationService;
import com.medassist.observation.domain.LabObservation;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/encounters/{encounterId}/labs")
@Tag(name = "Clinical data - Laboratory")
@RequireClinician
public class LabObservationController {

    private final LabObservationService service;
    private final ObservationMapper mapper;

    public LabObservationController(LabObservationService service, ObservationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a laboratory result")
    public ObservationDtos.LabResponse record(@PathVariable UUID encounterId,
                                              @Valid @RequestBody ObservationDtos.RecordLabRequest r) {
        var result = new LabObservation.Result(r.numericValue(), r.textValue(), r.unit(), r.referenceRangeLow(),
                r.referenceRangeHigh(), r.abnormalFlag(), r.collectedAt());
        return mapper.toResponse(service.record(encounterId, r.testName(), r.codingSystem(), r.testCode(), result,
                r.source(), r.sourceDetail()));
    }

    @GetMapping
    @Operation(summary = "List laboratory results (newest first)")
    public List<ObservationDtos.LabResponse> list(@PathVariable UUID encounterId,
                                                  @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.list(encounterId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/{labId}/entered-in-error")
    @Operation(summary = "Mark a lab result as entered in error")
    public ObservationDtos.LabResponse enteredInError(@PathVariable UUID encounterId, @PathVariable UUID labId,
                                                      @Valid @RequestBody CommonDtos.EnteredInErrorRequest request) {
        return mapper.toResponse(service.markEnteredInError(encounterId, labId, request.reason()));
    }
}
