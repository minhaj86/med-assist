package com.medassist.allergy.api;

import com.medassist.allergy.domain.AllergyCategory;
import com.medassist.allergy.domain.AllergySeverity;
import com.medassist.allergy.domain.AllergyStatus;
import com.medassist.common.api.CommonDtos;
import com.medassist.common.domain.DataProvenance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AllergyDtos {

    private AllergyDtos() {
    }

    public record AllergyRequest(@NotBlank @Size(max = 200) String allergen,
                                 AllergyCategory category,
                                 @Size(max = 300) String reaction,
                                 AllergySeverity severity,
                                 AllergyStatus status,
                                 DataProvenance source,
                                 @Size(max = 200) String sourceDetail) {
    }

    public record AllergyStatusRequest(@NotNull AllergyStatus status) {
    }

    public record AllergyResponse(UUID id, UUID patientId, UUID encounterId, String allergen, AllergyCategory category,
                                  String reaction, AllergySeverity severity, AllergyStatus status,
                                  CommonDtos.ProvenanceDto provenance, boolean enteredInError,
                                  String enteredInErrorReason, Instant createdAt) {
    }
}
