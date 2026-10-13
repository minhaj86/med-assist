package com.medassist.observation.domain;

import com.medassist.common.domain.ClinicalRecordEntity;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.terminology.domain.ClinicalConcept;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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

/**
 * Generic laboratory result. Any test is represented by a concept (LOINC where available) - labs are not
 * modelled as individual columns.
 */
@Entity
@Table(name = "lab_observations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LabObservation extends ClinicalRecordEntity {

    @Column(name = "encounter_id", nullable = false, updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "system", column = @Column(name = "test_coding_system", length = 30)),
            @AttributeOverride(name = "code", column = @Column(name = "test_code", length = 50)),
            @AttributeOverride(name = "displayName", column = @Column(name = "test_name", length = 200)),
            @AttributeOverride(name = "verificationStatus", column = @Column(name = "test_code_verification", length = 40))
    })
    private ClinicalConcept test;

    @Column(name = "numeric_value", precision = 14, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 500)
    private String textValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range_low", precision = 14, scale = 4)
    private BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high", precision = 14, scale = 4)
    private BigDecimal referenceRangeHigh;

    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_flag", length = 20)
    private AbnormalFlag abnormalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "abnormal_flag_source", nullable = false, length = 40)
    private AbnormalFlagSource abnormalFlagSource;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    public record Result(BigDecimal numericValue, String textValue, String unit, BigDecimal referenceRangeLow,
                         BigDecimal referenceRangeHigh, AbnormalFlag reportedFlag, Instant collectedAt) {
    }

    private LabObservation(UUID id, UUID encounterId, UUID patientId, ClinicalConcept test, Result result,
                           RecordProvenance provenance) {
        super(id, provenance);
        this.encounterId = Objects.requireNonNull(encounterId);
        this.patientId = Objects.requireNonNull(patientId);
        this.test = Objects.requireNonNull(test);
        if (result.numericValue() == null && (result.textValue() == null || result.textValue().isBlank())) {
            throw new DomainRuleViolationException("A lab result requires a numeric or text value");
        }
        if (result.referenceRangeLow() != null && result.referenceRangeHigh() != null
                && result.referenceRangeLow().compareTo(result.referenceRangeHigh()) > 0) {
            throw new DomainRuleViolationException("Reference range low must not exceed reference range high");
        }
        this.numericValue = result.numericValue();
        this.textValue = result.textValue() == null || result.textValue().isBlank() ? null : result.textValue().trim();
        this.unit = result.unit() == null || result.unit().isBlank() ? null : result.unit().trim();
        this.referenceRangeLow = result.referenceRangeLow();
        this.referenceRangeHigh = result.referenceRangeHigh();
        this.collectedAt = Objects.requireNonNull(result.collectedAt(), "collectedAt");
        if (result.reportedFlag() != null) {
            this.abnormalFlag = result.reportedFlag();
            this.abnormalFlagSource = AbnormalFlagSource.REPORTED;
        } else {
            this.abnormalFlag = deriveFlag(result.numericValue(), result.referenceRangeLow(), result.referenceRangeHigh());
            this.abnormalFlagSource = abnormalFlag == null ? AbnormalFlagSource.NONE
                    : AbnormalFlagSource.DERIVED_FROM_REFERENCE_RANGE;
        }
    }

    public static LabObservation record(UUID encounterId, UUID patientId, ClinicalConcept test, Result result,
                                        RecordProvenance provenance) {
        return new LabObservation(UUID.randomUUID(), encounterId, patientId, test, result, provenance);
    }

    /** Compares the value to the reference range supplied with the result; never infers critical values. */
    static AbnormalFlag deriveFlag(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value == null || (low == null && high == null)) {
            return null;
        }
        if (low != null && value.compareTo(low) < 0) {
            return AbnormalFlag.LOW;
        }
        if (high != null && value.compareTo(high) > 0) {
            return AbnormalFlag.HIGH;
        }
        return AbnormalFlag.NORMAL;
    }

    public boolean isAbnormal() {
        return abnormalFlag != null && abnormalFlag != AbnormalFlag.NORMAL;
    }

    public String displayValue() {
        String value = numericValue != null ? numericValue.stripTrailingZeros().toPlainString() : textValue;
        return unit == null ? value : value + " " + unit;
    }
}
