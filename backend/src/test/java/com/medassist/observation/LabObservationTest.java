package com.medassist.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.common.domain.DataProvenance;
import com.medassist.common.domain.RecordProvenance;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.observation.domain.AbnormalFlag;
import com.medassist.observation.domain.AbnormalFlagSource;
import com.medassist.observation.domain.LabObservation;
import com.medassist.terminology.domain.ClinicalConcept;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LabObservationTest {

    private static final RecordProvenance PROVENANCE =
            RecordProvenance.recorded(DataProvenance.LAB_IMPORT, "synthetic", "tester", Instant.EPOCH);

    private static LabObservation lab(String value, String low, String high, AbnormalFlag reported) {
        return LabObservation.record(UUID.randomUUID(), UUID.randomUUID(), ClinicalConcept.unverified("Test"),
                new LabObservation.Result(value == null ? null : new BigDecimal(value), value == null ? "positive" : null,
                        "unit", low == null ? null : new BigDecimal(low), high == null ? null : new BigDecimal(high),
                        reported, Instant.EPOCH), PROVENANCE);
    }

    @Test
    void derivesFlagFromSuppliedReferenceRange() {
        assertThat(lab("2", "3", "5", null).getAbnormalFlag()).isEqualTo(AbnormalFlag.LOW);
        assertThat(lab("6", "3", "5", null).getAbnormalFlag()).isEqualTo(AbnormalFlag.HIGH);
        LabObservation normal = lab("4", "3", "5", null);
        assertThat(normal.getAbnormalFlag()).isEqualTo(AbnormalFlag.NORMAL);
        assertThat(normal.getAbnormalFlagSource()).isEqualTo(AbnormalFlagSource.DERIVED_FROM_REFERENCE_RANGE);
    }

    @Test
    void neverInventsFlagsWithoutRangeAndNeverDerivesCriticalValues() {
        LabObservation noRange = lab("400", null, null, null);
        assertThat(noRange.getAbnormalFlag()).isNull();
        assertThat(noRange.getAbnormalFlagSource()).isEqualTo(AbnormalFlagSource.NONE);
        assertThat(lab("1000", "3", "5", null).getAbnormalFlag()).isEqualTo(AbnormalFlag.HIGH);
    }

    @Test
    void reportedFlagTakesPrecedence() {
        LabObservation reported = lab("7", "3", "5", AbnormalFlag.CRITICAL_HIGH);
        assertThat(reported.getAbnormalFlag()).isEqualTo(AbnormalFlag.CRITICAL_HIGH);
        assertThat(reported.getAbnormalFlagSource()).isEqualTo(AbnormalFlagSource.REPORTED);
    }

    @Test
    void rejectsInvertedReferenceRange() {
        assertThatThrownBy(() -> lab("4", "9", "5", null)).isInstanceOf(DomainRuleViolationException.class);
    }
}
