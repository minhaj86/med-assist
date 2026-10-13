package com.medassist.admin;

import static com.medassist.support.TestUsers.admin;
import static com.medassist.support.TestUsers.clinician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.AssessmentTestSupport;
import com.medassist.support.TestData;
import com.medassist.support.TestUsers;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AdminAndDashboardIT extends AbstractIntegrationTest {

    @Test
    void dashboardShowsTodaysEncountersWithLatestAssessment() throws Exception {
        UUID patientId = TestData.createPatient(mockMvc, jsonMapper);
        UUID encounterId = TestData.createEncounter(mockMvc, jsonMapper, patientId, "Dashboard synthetic encounter");
        AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, false);
        mockMvc.perform(get("/api/dashboard").with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todaysEncounters[*].encounterId", hasItem(encounterId.toString())))
                .andExpect(jsonPath("$.incompleteAssessments[*].encounterId", hasItem(encounterId.toString())))
                .andExpect(jsonPath("$.recentPatients[*].id", hasItem(patientId.toString())))
                .andExpect(jsonPath("$.recentAssessments[0].encounterId").value(encounterId.toString()));
        mockMvc.perform(get("/api/dashboard").with(admin())).andExpect(status().isForbidden());
    }

    @Test
    void adminSystemHealthAndConfigExposeNoSecrets() throws Exception {
        mockMvc.perform(get("/api/admin/system/health").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks[0].component").value("Database"))
                .andExpect(jsonPath("$.checks[0].status").value("UP"))
                .andExpect(jsonPath("$.checks[0].data.pgvector").exists());
        String config = mockMvc.perform(get("/api/admin/system/config").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProfiles[0]").value("test"))
                .andReturn().getResponse().getContentAsString();
        assertThat(config.toLowerCase()).doesNotContain("password").doesNotContain("api-key").doesNotContain("apikey");
        mockMvc.perform(get("/api/admin/system/health").with(clinician())).andExpect(status().isForbidden());
    }

    @Test
    void adminCanSuspendAndRestoreUserAccess() throws Exception {
        var suspended = TestUsers.user("clinician-suspend-test", "dr.suspend", "CLINICIAN");
        mockMvc.perform(get("/api/me").with(suspended)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").with(admin()))
                .andExpect(jsonPath("$[*].id", hasItem("clinician-suspend-test")));
        mockMvc.perform(put("/api/admin/users/{id}/access", "clinician-suspend-test").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(get("/api/patients").with(suspended))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account suspended"));
        mockMvc.perform(put("/api/admin/users/{id}/access", "clinician-suspend-test").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", true))))
                .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(get("/api/patients").with(suspended)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/audit-events").param("eventType", "USER_DISABLED").with(admin()))
                .andExpect(jsonPath("$.items[0].entityId").value("clinician-suspend-test"));
    }

    @Test
    void openApiDocumentationIsServed() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MedAssist Clinical Decision Support API"))
                .andExpect(jsonPath("$.paths['/api/encounters/{encounterId}/assessment']").exists());
    }
}
