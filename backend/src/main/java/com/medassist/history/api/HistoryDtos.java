package com.medassist.history.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.common.domain.DataProvenance;
import com.medassist.history.domain.HistoryCategory;
import com.medassist.history.domain.HistoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class HistoryDtos {

    private HistoryDtos() {
    }

    public record HistoryRequest(@NotNull HistoryCategory category,
                                 @NotBlank @Size(max = 500) String description,
                                 @Size(max = 50) String relationship,
                                 HistoryStatus status,
                                 @PastOrPresent LocalDate onsetDate,
                                 LocalDate endDate,
                                 @Size(max = 2000) String notes,
                                 DataProvenance source,
                                 @Size(max = 200) String sourceDetail,
                                 @Size(max = 200) String aiProposalReference) {
    }

    public record HistoryResponse(UUID id, UUID patientId, UUID encounterId, HistoryCategory category,
                                  CommonDtos.ConceptDto concept, String description, String relationship,
                                  HistoryStatus status, LocalDate onsetDate, LocalDate endDate, String notes,
                                  CommonDtos.ProvenanceDto provenance, boolean enteredInError,
                                  String enteredInErrorReason, Instant createdAt) {
    }
}
