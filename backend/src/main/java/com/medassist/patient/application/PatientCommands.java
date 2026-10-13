package com.medassist.patient.application;

import com.medassist.common.domain.DataProvenance;
import com.medassist.patient.domain.BiologicalSex;
import java.time.LocalDate;

public final class PatientCommands {

    private PatientCommands() {
    }

    public record RegisterPatient(String externalPatientId, String firstName, String lastName, LocalDate dateOfBirth,
                                  BiologicalSex biologicalSex, String gender, String contactPhone,
                                  boolean synthetic, DataProvenance provenance) {
    }

    public record UpdateDemographics(String firstName, String lastName, LocalDate dateOfBirth,
                                     BiologicalSex biologicalSex, String gender, String contactPhone) {
    }
}
