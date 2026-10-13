package com.medassist.clinicalrules;

import static com.medassist.support.TestUsers.admin;
import static com.medassist.support.TestUsers.clinician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class RuleAdministrationIT extends AbstractIntegrationTest {

    private static final String LOW_HR_V1 = "7f0c2a1e-0001-4a00-9000-000000000006";

    @Test
    void listsGovernedDemonstrationRules() throws Exception {
        mockMvc.perform(get("/api/admin/rules").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].ruleId", hasItem("DEMO-RESP-001")))
                .andExpect(jsonPath("$[0].demonstrationOnly").value(true))
                .andExpect(jsonPath("$[0].approvedBy").value("NOT CLINICALLY APPROVED (demonstration only)"));
        mockMvc.perform(get("/api/admin/rules").with(clinician())).andExpect(status().isForbidden());
    }

    @Test
    void changingARuleRequiresANewVersionAndHistoryIsPreserved() throws Exception {
        String body = json(Map.of("version", "1.1.0-demo", "severity", "URGENT",
                "parameters", Map.of("match", "ANY", "conditions", java.util.List.of(
                        Map.of("vitalSign", "HEART_RATE", "operator", "LT", "threshold", 45))),
                "description", "Example rule with an updated illustrative threshold.",
                "recommendedAction", "Demonstration alert: review per local protocol.",
                "source", "Integration test (synthetic)", "approvedBy", "NOT CLINICALLY APPROVED (test)",
                "demonstrationOnly", true, "changeReason", "Test new version", "enabled", true));
        mockMvc.perform(post("/api/admin/rules/{ruleId}/versions", "DEMO-CV-004").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("1.1.0-demo"))
                .andExpect(jsonPath("$.supersedesId").value(LOW_HR_V1))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));

        String all = mockMvc.perform(get("/api/admin/rules").with(admin())).andReturn().getResponse().getContentAsString();
        JsonNode old = null;
        for (JsonNode node : jsonMapper.readTree(all)) {
            if (node.get("id").asString().equals(LOW_HR_V1)) {
                old = node;
            }
        }
        assertThat(old).isNotNull();
        assertThat(old.get("lifecycleStatus").asString()).isEqualTo("SUPERSEDED");
        assertThat(old.get("parameters").toString()).contains("40");

        mockMvc.perform(post("/api/admin/rules/{ruleId}/versions", "DEMO-CV-004").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update clinical_rule_definitions set parameters = '{}'::jsonb where id = ?::uuid", LOW_HR_V1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("delete from clinical_rule_definitions where id = ?::uuid", LOW_HR_V1))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void invalidParametersAreRejected() throws Exception {
        String body = json(Map.of("version", "9.9.9-bad", "severity", "URGENT", "parameters", Map.of("match", "ANY"),
                "description", "d", "recommendedAction", "a", "source", "s", "approvedBy", "b",
                "demonstrationOnly", true, "changeReason", "bad", "enabled", false));
        mockMvc.perform(post("/api/admin/rules/{ruleId}/versions", "DEMO-RESP-002").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void disablingIsAnAuditedOperationalSwitch() throws Exception {
        String id = "7f0c2a1e-0001-4a00-9000-000000000007";
        mockMvc.perform(put("/api/admin/rules/{id}/enabled", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false, "reason", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(put("/api/admin/rules/{id}/enabled", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))))
                .andExpect(jsonPath("$.enabled").value(true));
        Integer audits = jdbcTemplate.queryForObject("select count(*) from audit_events where entity_id = ? "
                + "and event_type in ('CLINICAL_RULE_DISABLED','CLINICAL_RULE_ENABLED')", Integer.class, id);
        assertThat(audits).isEqualTo(2);
    }
}
