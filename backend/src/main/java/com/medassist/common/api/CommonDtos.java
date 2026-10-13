package com.medassist.common.api;

import com.medassist.common.domain.DataProvenance;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class CommonDtos {

    private CommonDtos() {
    }

    public record ProvenanceDto(DataProvenance source, String sourceDetail, String recordedBy, Instant recordedAt,
                                String confirmedBy, Instant confirmedAt) {
    }

    public record ConceptDto(CodingSystem system, String code, String displayName,
                             ConceptVerificationStatus verificationStatus) {
    }

    public record EnteredInErrorRequest(@NotBlank @Size(max = 500) String reason) {
    }
}
