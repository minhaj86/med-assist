package com.medassist.observation.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.observation.application.VitalSignService;
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
@RequestMapping("/api/encounters/{encounterId}/observations")
@Tag(name = "Clinical data - Vital signs")
@RequireClinician
public class VitalSignController {

    private final VitalSignService service;
    private final ObservationMapper mapper;

    public VitalSignController(VitalSignService service, ObservationMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a set of vital signs measured at the same time")
    public List<ObservationDtos.ObservationResponse> record(@PathVariable UUID encounterId,
                                                            @Valid @RequestBody ObservationDtos.RecordVitalSignsRequest request) {
        var measurements = request.measurements().stream()
                .map(m -> new VitalSignService.Measurement(m.type(), m.value(), m.consciousnessLevel()))
                .toList();
        return service.record(encounterId, request.measuredAt(), measurements, request.source(),
                request.sourceDetail()).stream().map(mapper::toResponse).toList();
    }

    @GetMapping
    @Operation(summary = "List vital sign observations (newest first)")
    public List<ObservationDtos.ObservationResponse> list(@PathVariable UUID encounterId,
                                                          @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.list(encounterId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/{observationId}/entered-in-error")
    @Operation(summary = "Mark an observation as entered in error")
    public ObservationDtos.ObservationResponse enteredInError(@PathVariable UUID encounterId,
                                                              @PathVariable UUID observationId,
                                                              @Valid @RequestBody CommonDtos.EnteredInErrorRequest request) {
        return mapper.toResponse(service.markEnteredInError(encounterId, observationId, request.reason()));
    }
}
