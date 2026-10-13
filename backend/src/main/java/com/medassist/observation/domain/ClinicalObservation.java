package com.medassist.observation.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A single clinical observation (currently vital signs) with value, unit, time and source. */
@Entity
@Table(name = "observations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalObservation extends ClinicalRecordEntity {

    @Column(name = "encounter_id", nullable = false, updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ObservationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "vital_sign_type", nullable = false, length = 40)
    private VitalSignType vitalSignType;

    @Embedded
    private ClinicalConcept concept;

    @Column(name = "numeric_value", precision = 10, scale = 3)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 100)
    private String textValue;

    @Column(name = "unit", length = 30)
    private String unit;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;

    private ClinicalObservation(UUID id, UUID encounterId, UUID patientId, VitalSignType type,
                                BigDecimal numericValue, String textValue, Instant measuredAt,
                                RecordProvenance provenance) {
        super(id, provenance);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.patientId = Objects.requireNonNull(patientId);
        this.category = ObservationCategory.VITAL_SIGN;
        this.vitalSignType = Objects.requireNonNull(type);
        this.concept = type.loincCode() == null
                ? ClinicalConcept.unverified(type.displayName())
                : new ClinicalConcept(CodingSystem.LOINC, type.loincCode(), type.displayName(),
                ConceptVerificationStatus.CURATED_REFERENCE);
        this.numericValue = numericValue;
        this.textValue = textValue;
        this.unit = type.unit();
        this.measuredAt = Objects.requireNonNull(measuredAt);
    }

    public static ClinicalObservation numericVitalSign(UUID encounterId, UUID patientId, VitalSignType type,
                                                       BigDecimal value, Instant measuredAt,
                                                       RecordProvenance provenance) {
        if (type.isCoded()) {
            throw new DomainRuleViolationException(type.displayName() + " requires a coded value");
        }
        if (!type.isPlausible(value)) {
            throw new DomainRuleViolationException(type.displayName() + " value " + value + " " + type.unit()
                    + " is outside the plausible range " + type.plausibleMin() + "-" + type.plausibleMax()
                    + " - please check the entry");
        }
        return new ClinicalObservation(UUID.randomUUID(), encounterId, patientId, type, value, null, measuredAt,
                provenance);
    }

    public static ClinicalObservation consciousnessLevel(UUID encounterId, UUID patientId, ConsciousnessLevel level,
                                                         Instant measuredAt, RecordProvenance provenance) {
        return new ClinicalObservation(UUID.randomUUID(), encounterId, patientId, VitalSignType.CONSCIOUSNESS_LEVEL,
                null, Objects.requireNonNull(level).name(), measuredAt, provenance);
    }

    public ConsciousnessLevel consciousnessLevel() {
        return vitalSignType == VitalSignType.CONSCIOUSNESS_LEVEL && textValue != null
                ? ConsciousnessLevel.valueOf(textValue) : null;
    }

    /** Human readable value with unit, e.g. "92 %" or "Responds to voice". */
    public String displayValue() {
        if (vitalSignType == VitalSignType.CONSCIOUSNESS_LEVEL) {
            ConsciousnessLevel level = consciousnessLevel();
            return level == null ? textValue : level.displayName();
        }
        return numericValue.stripTrailingZeros().toPlainString() + " " + unit;
    }
}
