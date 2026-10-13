package com.medassist.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class AuditTrailImmutabilityIT extends AbstractIntegrationTest {

    @Test
    void auditEventsCannotBeUpdatedOrDeleted() throws Exception {
        UUID patientId = TestData.createPatient(mockMvc, jsonMapper);
        Integer count = jdbcTemplate.queryForObject("select count(*) from audit_events where patient_id = ?",
                Integer.class, patientId);
        assertThat(count).isPositive();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update audit_events set event_type = 'TAMPERED' where patient_id = ?", patientId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("delete from audit_events where patient_id = ?", patientId))
                .isInstanceOf(DataAccessException.class);
    }
}
