package com.medassist.allergy.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "allergies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Allergy extends ClinicalRecordEntity {

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "allergen", nullable = false, length = 200)
    private String allergen;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private AllergyCategory category;

    @Column(name = "reaction", length = 300)
    private String reaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AllergySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AllergyStatus status;

    private Allergy(UUID id, UUID patientId, UUID encounterId, String allergen, AllergyCategory category,
                    String reaction, AllergySeverity severity, AllergyStatus status, RecordProvenance provenance) {
        super(id, provenance);
        if (allergen == null || allergen.isBlank()) {
            throw new DomainRuleViolationException("Allergen is required");
        }
        this.patientId = Objects.requireNonNull(patientId);
        this.encounterId = encounterId;
        this.allergen = allergen.trim();
        this.category = category;
        this.reaction = reaction;
        this.severity = severity == null ? AllergySeverity.UNKNOWN : severity;
        this.status = status == null ? AllergyStatus.ACTIVE : status;
    }

    public static Allergy record(UUID patientId, UUID encounterId, String allergen, AllergyCategory category,
                                 String reaction, AllergySeverity severity, AllergyStatus status,
                                 RecordProvenance provenance) {
        return new Allergy(UUID.randomUUID(), patientId, encounterId, allergen, category, reaction, severity, status,
                provenance);
    }

    public void changeStatus(AllergyStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public boolean isActive() {
        return status == AllergyStatus.ACTIVE && !isEnteredInError();
    }
}
