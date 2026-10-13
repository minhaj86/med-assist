package com.medassist.history.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.terminology.domain.ClinicalConcept;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

/** Past medical, family, surgical and social history (smoking, alcohol, hospitalizations). */
@Entity
@Table(name = "medical_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicalHistoryEntry extends ClinicalRecordEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private HistoryCategory category;

    @Embedded
    private ClinicalConcept concept;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "relationship", length = 50)
    private String relationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HistoryStatus status;

    @Column(name = "onset_date")
    private LocalDate onsetDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "notes", length = 2000)
    private String notes;

    public record Details(HistoryCategory category, String description, String relationship, HistoryStatus status,
                          LocalDate onsetDate, LocalDate endDate, String notes) {
    }

    private MedicalHistoryEntry(UUID id, UUID patientId, UUID encounterId, ClinicalConcept concept, Details d,
                                RecordProvenance provenance) {
        super(id, provenance);
        if (d.description() == null || d.description().isBlank()) {
            throw new DomainRuleViolationException("History description is required");
        }
        if (d.category() == HistoryCategory.FAMILY_HISTORY && (d.relationship() == null || d.relationship().isBlank())) {
            throw new DomainRuleViolationException("Family history requires the relationship to the patient");
        }
        if (d.onsetDate() != null && d.endDate() != null && d.endDate().isBefore(d.onsetDate())) {
            throw new DomainRuleViolationException("End date cannot precede onset date");
        }
        this.patientId = Objects.requireNonNull(patientId);
        this.encounterId = encounterId;
        this.category = Objects.requireNonNull(d.category());
        this.concept = concept;
        this.description = d.description().trim();
        this.relationship = d.relationship();
        this.status = d.status() == null ? HistoryStatus.UNKNOWN : d.status();
        this.onsetDate = d.onsetDate();
        this.endDate = d.endDate();
        this.notes = d.notes();
    }

    public static MedicalHistoryEntry record(UUID patientId, UUID encounterId, ClinicalConcept concept, Details details,
                                             RecordProvenance provenance) {
        return new MedicalHistoryEntry(UUID.randomUUID(), patientId, encounterId, concept, details, provenance);
    }
}
