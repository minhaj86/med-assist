package com.medassist.patient.api;

import com.medassist.patient.domain.BiologicalSex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PatientDtos {

    private PatientDtos() {
    }

    public record CreatePatientRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,64}$",
                    message = "must be 3-64 characters: letters, digits or hyphens") String externalPatientId,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull BiologicalSex biologicalSex,
            @Size(max = 50) String gender,
            @Size(max = 40) @Pattern(regexp = "^[0-9 +()-]*$", message = "must be a phone number") String contactPhone) {
    }

    public record UpdatePatientRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            @NotNull BiologicalSex biologicalSex,
            @Size(max = 50) String gender,
            @Size(max = 40) @Pattern(regexp = "^[0-9 +()-]*$", message = "must be a phone number") String contactPhone) {
    }

    public record PatientResponse(UUID id, String externalPatientId, String firstName, String lastName,
                                  LocalDate dateOfBirth, int ageYears, BiologicalSex biologicalSex, String gender,
                                  String contactPhone, boolean synthetic, String provenance, Instant createdAt,
                                  Instant updatedAt) {
    }

    public record PatientSummary(UUID id, String externalPatientId, String firstName, String lastName,
                                 LocalDate dateOfBirth, int ageYears, BiologicalSex biologicalSex,
                                 boolean synthetic) {
    }
}
