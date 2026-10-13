package com.medassist.medication.api;

import com.medassist.common.api.CommonDtos;
import com.medassist.common.domain.DataProvenance;
import com.medassist.medication.domain.MedicationStatus;
import com.medassist.terminology.domain.CodingSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class MedicationDtos {

    private MedicationDtos() {
    }

    public record MedicationRequest(@NotBlank @Size(max = 200) String medicationName,
                                    CodingSystem codingSystem,
                                    @Size(max = 50) String code,
                                    @Size(max = 100) String dosage,
                                    @Size(max = 100) String frequency,
                                    @Size(max = 50) String route,
                                    LocalDate startDate,
                                    LocalDate endDate,
                                    MedicationStatus status,
                                    DataProvenance source,
                                    @Size(max = 200) String sourceDetail) {
    }

    public record MedicationStatusRequest(@NotNull MedicationStatus status, LocalDate endDate) {
    }

    public record MedicationResponse(UUID id, UUID patientId, UUID encounterId, String medicationName,
                                     CodingSystem codingSystem, String code, String dosage, String frequency,
                                     String route, LocalDate startDate, LocalDate endDate, MedicationStatus status,
                                     CommonDtos.ProvenanceDto provenance, boolean enteredInError,
                                     String enteredInErrorReason, Instant createdAt, Instant updatedAt) {
    }
}
