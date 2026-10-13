package com.medassist.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.common.domain.DataProvenance;
import com.medassist.common.domain.RecordProvenance;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecordProvenanceTest {

    @Test
    void aiInferredDataCannotBeRecordedWithoutConfirmation() {
        assertThatThrownBy(() -> RecordProvenance.recorded(DataProvenance.AI_INFERRED, null, "u", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmedAiProposalRecordsTheConfirmingClinician() {
        RecordProvenance provenance = RecordProvenance.confirmedAiProposal("ref", "clinician-1", Instant.EPOCH);
        assertThat(provenance.getSource()).isEqualTo(DataProvenance.AI_INFERRED);
        assertThat(provenance.getConfirmedBy()).isEqualTo("clinician-1");
        assertThat(provenance.getConfirmedAt()).isEqualTo(Instant.EPOCH);
    }
}
