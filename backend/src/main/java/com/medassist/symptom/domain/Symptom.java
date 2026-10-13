package com.medassist.symptom.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.terminology.domain.ClinicalConcept;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A structured symptom (or pertinent negative). Any symptom concept is allowed - not a fixed list. */
@Entity
@Table(name = "symptoms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Symptom extends ClinicalRecordEntity {

    @Column(name = "encounter_id", nullable = false, updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Embedded
    private ClinicalConcept concept;

    @Enumerated(EnumType.STRING)
    @Column(name = "presence", nullable = false, length = 10)
    private SymptomPresence presence;

    @Enumerated(EnumType.STRING)
    @Column(name = "onset_pattern", length = 20)
    private OnsetPattern onsetPattern;

    @Column(name = "onset_at")
    private Instant onsetAt;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "severity")
    private Integer severity;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "character", length = 200)
    private String character;

    @Column(name = "radiation", length = 200)
    private String radiation;

    @Column(name = "aggravating_factors", length = 500)
    private String aggravatingFactors;

    @Column(name = "relieving_factors", length = 500)
    private String relievingFactors;

    @Column(name = "associated_symptoms", length = 500)
    private String associatedSymptoms;

    @Column(name = "notes", length = 2000)
    private String notes;

    public record Details(SymptomPresence presence, OnsetPattern onsetPattern, Instant onsetAt, String duration,
                          Integer severity, String location, String character, String radiation,
                          String aggravatingFactors, String relievingFactors, String associatedSymptoms,
                          String notes) {
    }

    private Symptom(UUID id, UUID encounterId, UUID patientId, ClinicalConcept concept, Details details,
                    RecordProvenance provenance) {
        super(id, provenance);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.patientId = Objects.requireNonNull(patientId);
        this.concept = Objects.requireNonNull(concept);
        apply(details);
    }

    public static Symptom record(UUID encounterId, UUID patientId, ClinicalConcept concept, Details details,
                                 RecordProvenance provenance) {
        return new Symptom(UUID.randomUUID(), encounterId, patientId, concept, details, provenance);
    }

    public void revise(ClinicalConcept concept, Details details) {
        if (isEnteredInError()) {
            throw new IllegalStateException("Cannot revise a record marked as entered in error");
        }
        this.concept = Objects.requireNonNull(concept);
        apply(details);
    }

    private void apply(Details d) {
        this.presence = Objects.requireNonNull(d.presence(), "presence");
        if (d.severity() != null && (d.severity() < 0 || d.severity() > 10)) {
            throw new IllegalArgumentException("Severity must be between 0 and 10");
        }
        this.onsetPattern = d.onsetPattern();
        this.onsetAt = d.onsetAt();
        this.duration = trim(d.duration());
        this.severity = d.severity();
        this.location = trim(d.location());
        this.character = trim(d.character());
        this.radiation = trim(d.radiation());
        this.aggravatingFactors = trim(d.aggravatingFactors());
        this.relievingFactors = trim(d.relievingFactors());
        this.associatedSymptoms = trim(d.associatedSymptoms());
        this.notes = trim(d.notes());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
