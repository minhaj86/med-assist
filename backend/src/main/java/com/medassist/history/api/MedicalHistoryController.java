package com.medassist.history.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.history.application.MedicalHistoryService;
import com.medassist.history.domain.MedicalHistoryEntry;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Clinical data - Medical history")
@RequireClinician
public class MedicalHistoryController {

    private final MedicalHistoryService service;
    private final HistoryMapper mapper;

    public MedicalHistoryController(MedicalHistoryService service, HistoryMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/api/encounters/{encounterId}/history")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record medical, family, surgical or social history")
    public HistoryDtos.HistoryResponse record(@PathVariable UUID encounterId,
                                              @Valid @RequestBody HistoryDtos.HistoryRequest r) {
        var details = new MedicalHistoryEntry.Details(r.category(), r.description(), r.relationship(), r.status(),
                r.onsetDate(), r.endDate(), r.notes());
        return mapper.toResponse(service.record(encounterId, details, r.source(), r.sourceDetail(),
                r.aiProposalReference()));
    }

    @GetMapping("/api/patients/{patientId}/history")
    @Operation(summary = "List the patient's history")
    public List<HistoryDtos.HistoryResponse> list(@PathVariable UUID patientId,
                                                  @RequestParam(defaultValue = "false") boolean includeEnteredInError) {
        return service.listForPatient(patientId, includeEnteredInError).stream().map(mapper::toResponse).toList();
    }

    @PostMapping("/api/history/{entryId}/entered-in-error")
    @Operation(summary = "Mark a history entry as entered in error")
    public HistoryDtos.HistoryResponse enteredInError(@PathVariable UUID entryId,
                                                      @Valid @RequestBody CommonDtos.EnteredInErrorRequest r) {
        return mapper.toResponse(service.markEnteredInError(entryId, r.reason()));
    }
}
