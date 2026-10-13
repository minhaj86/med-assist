package com.medassist.medication.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.terminology.domain.CodingSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A medication on the patient's medication list (recorded during an encounter). */
@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Medication extends ClinicalRecordEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "coding_system", length = 30)
    private CodingSystem codingSystem;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "dosage", length = 100)
    private String dosage;

    @Column(name = "frequency", length = 100)
    private String frequency;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MedicationStatus status;

    public record Details(String medicationName, CodingSystem codingSystem, String code, String dosage,
                          String frequency, String route, LocalDate startDate, LocalDate endDate,
                          MedicationStatus status) {
    }

    private Medication(UUID id, UUID patientId, UUID encounterId, Details d, RecordProvenance provenance) {
        super(id, provenance);
        this.patientId = Objects.requireNonNull(patientId);
        this.encounterId = encounterId;
        if (d.medicationName() == null || d.medicationName().isBlank()) {
            throw new DomainRuleViolationException("Medication name is required");
        }
        if (d.code() != null && !d.code().isBlank() && d.codingSystem() == null) {
            throw new DomainRuleViolationException("A medication code requires a coding system");
        }
        this.medicationName = d.medicationName().trim();
        this.codingSystem = d.codingSystem();
        this.code = d.code() == null || d.code().isBlank() ? null : d.code().trim();
        this.dosage = d.dosage();
        this.frequency = d.frequency();
        this.route = d.route();
        this.startDate = d.startDate();
        changeStatus(d.status() == null ? MedicationStatus.ACTIVE : d.status(), d.endDate());
    }

    public static Medication record(UUID patientId, UUID encounterId, Details details, RecordProvenance provenance) {
        return new Medication(UUID.randomUUID(), patientId, encounterId, details, provenance);
    }

    public void changeStatus(MedicationStatus newStatus, LocalDate newEndDate) {
        if (newEndDate != null && startDate != null && newEndDate.isBefore(startDate)) {
            throw new DomainRuleViolationException("End date cannot precede start date");
        }
        this.status = Objects.requireNonNull(newStatus);
        this.endDate = newEndDate;
    }

    public boolean isActive() {
        return status == MedicationStatus.ACTIVE && !isEnteredInError();
    }
}
