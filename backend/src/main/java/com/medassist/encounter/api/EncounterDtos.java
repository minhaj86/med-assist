package com.medassist.encounter.api;

import com.medassist.encounter.domain.EncounterStatus;
import com.medassist.encounter.domain.EncounterType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class EncounterDtos {

    private EncounterDtos() {
    }

    public record CreateEncounterRequest(@NotNull EncounterType encounterType,
                                         @Size(max = 500) String chiefComplaint,
                                         @PastOrPresent Instant startedAt) {
    }

    public record UpdateEncounterRequest(@Size(max = 500) String chiefComplaint) {
    }

    public record EncounterResponse(UUID id, UUID patientId, EncounterType encounterType, EncounterStatus status,
                                    String chiefComplaint, Instant startedAt, Instant completedAt,
                                    String clinicianId, String provenance, Instant createdAt, Instant updatedAt) {
    }
}
