package com.medassist.investigation.api;

import com.medassist.assessment.domain.ProposedBy;
import com.medassist.common.json.JsonCodec;
import com.medassist.investigation.application.InvestigationService;
import com.medassist.investigation.domain.Investigation;
import com.medassist.investigation.domain.InvestigationStatus;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@Tag(name = "Investigations")
@RequireClinician
public class InvestigationController {

    public record InvestigationResponse(UUID id, UUID encounterId, String name, ProposedBy proposedBy, String reason,
                                        List<String> supportingEvidence, InvestigationStatus status,
                                        UUID sourceSuggestionId, UUID sourceAssessmentVersionId, String createdBy,
                                        Instant createdAt, Instant updatedAt) {
    }

    public record CreateInvestigationRequest(@NotBlank @Size(max = 300) String name, @Size(max = 2000) String reason,
                                             @NotNull InvestigationStatus status) {
    }

    public record StatusRequest(@NotNull InvestigationStatus status, @Size(max = 1000) String note) {
    }

    private final InvestigationService service;
    private final JsonCodec json;

    public InvestigationController(InvestigationService service, JsonCodec json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping("/api/encounters/{encounterId}/investigations")
    @Operation(summary = "Investigations in the encounter workflow")
    public List<InvestigationResponse> list(@PathVariable UUID encounterId) {
        return service.list(encounterId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/api/encounters/{encounterId}/investigations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a clinician-proposed investigation")
    public InvestigationResponse create(@PathVariable UUID encounterId,
                                        @Valid @RequestBody CreateInvestigationRequest r) {
        return toResponse(service.addClinicianInvestigation(encounterId, r.name(), r.reason(), r.status()));
    }

    @PatchMapping("/api/investigations/{investigationId}/status")
    @Operation(summary = "Change investigation status (clinician action; records history)")
    public InvestigationResponse changeStatus(@PathVariable UUID investigationId, @Valid @RequestBody StatusRequest r) {
        return toResponse(service.changeStatus(investigationId, r.status(), r.note()));
    }

    private InvestigationResponse toResponse(Investigation i) {
        return new InvestigationResponse(i.getId(), i.getEncounterId(), i.getName(), i.getProposedBy(), i.getReason(),
                json.read(i.getSupportingEvidence(), new TypeReference<List<String>>() {
                }), i.getStatus(), i.getSourceSuggestionId(), i.getSourceAssessmentVersionId(), i.getCreatedBy(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
