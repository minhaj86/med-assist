package com.medassist.patient.domain;

import com.medassist.common.domain.AuditableEntity;
import com.medassist.common.domain.DataProvenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Patient aggregate. Holds only the identifying data the decision-support workflow needs. */
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient extends AuditableEntity {

    @Column(name = "external_patient_id", nullable = false, length = 64, unique = true)
    private String externalPatientId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "biological_sex", nullable = false, length = 20)
    private BiologicalSex biologicalSex;

    @Column(name = "gender", length = 50)
    private String gender;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    /** True for fictional demonstration patients. Rendered as "SYNTHETIC TEST DATA" in all clients. */
    @Column(name = "synthetic", nullable = false)
    private boolean synthetic;

    @Enumerated(EnumType.STRING)
    @Column(name = "provenance", nullable = false, length = 30)
    private DataProvenance provenance;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    private Patient(UUID id, String externalPatientId, String firstName, String lastName, LocalDate dateOfBirth,
                    BiologicalSex biologicalSex, String gender, String contactPhone, boolean synthetic,
                    DataProvenance provenance, String createdBy) {
        super(id);
        this.externalPatientId = requireText(externalPatientId, "externalPatientId");
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        this.biologicalSex = Objects.requireNonNull(biologicalSex, "biologicalSex");
        this.gender = blankToNull(gender);
        this.contactPhone = blankToNull(contactPhone);
        this.synthetic = synthetic;
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    }

    public static Patient register(String externalPatientId, String firstName, String lastName, LocalDate dateOfBirth,
                                   BiologicalSex biologicalSex, String gender, String contactPhone,
                                   boolean synthetic, DataProvenance provenance, String createdBy) {
        return new Patient(UUID.randomUUID(), externalPatientId, firstName, lastName, dateOfBirth, biologicalSex,
                gender, contactPhone, synthetic, provenance, createdBy);
    }

    public void updateDemographics(String firstName, String lastName, LocalDate dateOfBirth,
                                   BiologicalSex biologicalSex, String gender, String contactPhone) {
        this.firstName = requireText(firstName, "firstName");
        this.lastName = requireText(lastName, "lastName");
        this.dateOfBirth = Objects.requireNonNull(dateOfBirth, "dateOfBirth");
        this.biologicalSex = Objects.requireNonNull(biologicalSex, "biologicalSex");
        this.gender = blankToNull(gender);
        this.contactPhone = blankToNull(contactPhone);
    }

    public int ageOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
