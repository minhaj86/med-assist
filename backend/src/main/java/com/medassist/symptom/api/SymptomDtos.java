package com.medassist.symptom.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.common.domain.DataProvenance;
import com.medassist.symptom.domain.OnsetPattern;
import com.medassist.symptom.domain.SymptomPresence;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SymptomDtos {

    private SymptomDtos() {
    }

    public record SymptomRequest(
            @NotBlank @Size(max = 200) String symptom,
            @NotNull SymptomPresence presence,
            OnsetPattern onsetPattern,
            @PastOrPresent Instant onsetAt,
            @Size(max = 100) String duration,
            @Min(0) @Max(10) Integer severity,
            @Size(max = 200) String location,
            @Size(max = 200) String character,
            @Size(max = 200) String radiation,
            @Size(max = 500) String aggravatingFactors,
            @Size(max = 500) String relievingFactors,
            @Size(max = 500) String associatedSymptoms,
            @Size(max = 2000) String notes,
            DataProvenance source,
            @Size(max = 200) String sourceDetail,
            @Size(max = 200) String aiProposalReference) {
    }

    public record SymptomResponse(UUID id, UUID encounterId, CommonDtos.ConceptDto concept, SymptomPresence presence,
                                  OnsetPattern onsetPattern, Instant onsetAt, String duration, Integer severity,
                                  String location, String character, String radiation, String aggravatingFactors,
                                  String relievingFactors, String associatedSymptoms, String notes,
                                  CommonDtos.ProvenanceDto provenance, boolean enteredInError,
                                  String enteredInErrorReason, Instant createdAt, Instant updatedAt) {
    }
}
