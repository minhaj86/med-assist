package com.medassist.assessment;

import static com.medassist.support.TestUsers.clinician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.AssessmentTestSupport;
import com.medassist.support.TestData;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** Synthetic case: chest pain, dyspnoea, tachycardia and low SpO2 (illustrative values only). */
class ClinicalAssessmentIT extends AbstractIntegrationTest {

    private UUID encounterId;

    @BeforeEach
    void setUp() throws Exception {
        UUID patientId = TestData.createPatient(mockMvc, jsonMapper);
        encounterId = TestData.createEncounter(mockMvc, jsonMapper, patientId, "Chest pain (synthetic)");
        post("/api/encounters/{id}/symptoms", Map.of("symptom", "Chest pain", "presence", "PRESENT", "severity", 7));
        post("/api/encounters/{id}/symptoms", Map.of("symptom", "Shortness of breath", "presence", "PRESENT"));
        post("/api/encounters/{id}/observations", Map.of("measurements", List.of(
                Map.of("type", "HEART_RATE", "value", 135),
                Map.of("type", "OXYGEN_SATURATION", "value", 88),
                Map.of("type", "SYSTOLIC_BLOOD_PRESSURE", "value", 128),
                Map.of("type", "RESPIRATORY_RATE", "value", 24),
                Map.of("type", "BODY_TEMPERATURE", "value", 37.2),
                Map.of("type", "CONSCIOUSNESS_LEVEL", "consciousnessLevel", "ALERT"))));
    }

    private void post(String url, Object body) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url, encounterId)
                        .with(clinician()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
    }

    @Test
    void liveSafetyAlertsAreDeterministicAndIndependentOfAi() throws Exception {
        mockMvc.perform(get("/api/encounters/{id}/safety-alerts", encounterId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rulesEvaluated").value(11))
                .andExpect(jsonPath("$.alerts[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.alerts[0].origin").value("RULE"))
                .andExpect(jsonPath("$.alerts[0].demonstrationOnly").value(true))
                .andExpect(jsonPath("$.alerts[*].sourceReference", hasItem("DEMO-RESP-001 v1.0.0-demo")))
                .andExpect(jsonPath("$.alerts[*].sourceReference", hasItem("DEMO-CV-003 v1.0.0-demo")))
                .andExpect(jsonPath("$.scores[0].demonstrationOnly").value(true));
    }

    @Test
    void assessmentProducesImmutableVersionsWithChangeTracking() throws Exception {
        JsonNode run1 = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, false);
        assertThat(run1.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run1.get("stages").toString()).contains("RUNNING_RULES").contains("SEARCHING_KNOWLEDGE");
        String v1 = run1.get("versionId").asString();

        mockMvc.perform(get("/api/assessments/{id}", v1).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.ai.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.safetyAlerts[0].origin").value("RULE"))
                .andExpect(jsonPath("$.aiRedFlags", hasSize(0)))
                .andExpect(jsonPath("$.executedRules", hasSize(11)))
                .andExpect(jsonPath("$.facts[0].key").value("F1"))
                .andExpect(jsonPath("$.changes.previousVersionNumber").doesNotExist());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/encounters/{id}/labs", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("testName", "Troponin", "numericValue", 8, "unit", "ng/L",
                                "referenceRangeHigh", 14, "collectedAt", Instant.now().minusSeconds(60).toString()))))
                .andExpect(status().isCreated());

        JsonNode run2 = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, true);
        String v2 = run2.get("versionId").asString();
        mockMvc.perform(get("/api/assessments/{id}", v2).with(clinician()))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.changes.previousVersionNumber").value(1))
                .andExpect(jsonPath("$.changes.inputChanges[0].type").value("ADDED"))
                .andExpect(jsonPath("$.changes.inputChanges[0].category").value("LAB_RESULT"));

        mockMvc.perform(get("/api/encounters/{id}/assessments", encounterId).with(clinician()))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber").value(2));

        assertThatThrownBy(() -> jdbcTemplate.update("update assessment_versions set created_by = 'x' where id = ?::uuid", v1))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update clinical_rule_results set explanation = 'x' where assessment_version_id = ?::uuid", v1))
                .isInstanceOf(DataAccessException.class);

        Integer audits = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where encounter_id = ? and event_type = 'ASSESSMENT_COMPLETED'",
                Integer.class, encounterId);
        assertThat(audits).isEqualTo(2);
    }

    @Test
    void finalizationRequiresExplicitAttestationAndLocksTheAssessment() throws Exception {
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, false);
        String versionId = run.get("versionId").asString();

        Map<String, Object> request = new java.util.HashMap<>(Map.of("reviewedVersionId", versionId,
                "summary", "Synthetic summary documented by clinician", "plan", "Synthetic plan",
                "diagnoses", List.of(Map.of("conditionName", "Chest pain, cause under evaluation",
                        "certainty", "PROVISIONAL")), "attestation", false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/encounters/{id}/final-assessment", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isBadRequest());

        request.put("attestation", true);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/encounters/{id}/final-assessment", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.finalizedBy").value("clinician-test-1"))
                .andExpect(jsonPath("$.diagnoses[0].codeVerification").value("UNVERIFIED"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/encounters/{id}/assessment", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void clinicianControlsInvestigationWorkflow() throws Exception {
        String created = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/encounters/{id}/investigations", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "12-lead ECG", "status", "ACCEPTED"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proposedBy").value("CLINICIAN"))
                .andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(created).get("id").asString();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/investigations/{id}/status", id).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "COMPLETED"))))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/investigations/{id}/status", id).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ORDERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"));
    }
}
