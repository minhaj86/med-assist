package com.medassist.encounter;

import static com.medassist.support.TestUsers.clinician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.TestData;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class EncounterClinicalDataIT extends AbstractIntegrationTest {

    private UUID patientId;
    private UUID encounterId;

    @BeforeEach
    void setUp() throws Exception {
        patientId = TestData.createPatient(mockMvc, jsonMapper);
        encounterId = TestData.createEncounter(mockMvc, jsonMapper, patientId, "Chest discomfort (synthetic case)");
    }

    private ResultActions postJson(String url, Object body, Object... vars) throws Exception {
        return mockMvc.perform(post(url, vars).with(clinician()).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    @Test
    void recordsStructuredClinicalDataAndReturnsChart() throws Exception {
        String now = Instant.now().minus(5, ChronoUnit.MINUTES).toString();

        postJson("/api/encounters/{id}/symptoms", Map.of("symptom", "chest tightness", "presence", "PRESENT",
                "onsetPattern", "SUDDEN", "severity", 7, "radiation", "left arm"), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.concept.displayName").value("Chest pain"))
                .andExpect(jsonPath("$.concept.code").doesNotExist())
                .andExpect(jsonPath("$.concept.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.provenance.source").value("CLINICIAN"));

        postJson("/api/encounters/{id}/symptoms", Map.of("symptom", "Fever", "presence", "ABSENT"), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.presence").value("ABSENT"));

        postJson("/api/encounters/{id}/observations", Map.of("measuredAt", now, "measurements", List.of(
                Map.of("type", "HEART_RATE", "value", 118),
                Map.of("type", "OXYGEN_SATURATION", "value", 93),
                Map.of("type", "SYSTOLIC_BLOOD_PRESSURE", "value", 142),
                Map.of("type", "CONSCIOUSNESS_LEVEL", "consciousnessLevel", "ALERT"))), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].concept.code").value("8867-4"))
                .andExpect(jsonPath("$[0].concept.verificationStatus").value("CURATED_REFERENCE"))
                .andExpect(jsonPath("$[0].unit").value("/min"));

        postJson("/api/encounters/{id}/labs", Map.of("testName", "hs troponin", "numericValue", 35, "unit", "ng/L",
                "referenceRangeHigh", 14, "collectedAt", now), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abnormalFlag").value("HIGH"))
                .andExpect(jsonPath("$.abnormalFlagSource").value("DERIVED_FROM_REFERENCE_RANGE"))
                .andExpect(jsonPath("$.testCode").doesNotExist());

        postJson("/api/encounters/{id}/labs", Map.of("testName", "Lactate", "codingSystem", "LOINC", "testCode",
                "9999-9", "numericValue", 1.1, "collectedAt", now), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codeVerification").value("PROVIDED_UNVERIFIED"));

        postJson("/api/encounters/{id}/medications", Map.of("medicationName", "Synthetic statin", "dosage", "20 mg",
                "frequency", "once daily", "route", "oral"), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        postJson("/api/encounters/{id}/allergies", Map.of("allergen", "Penicillin (synthetic)", "category", "MEDICATION",
                "reaction", "rash", "severity", "MODERATE"), encounterId)
                .andExpect(status().isCreated());

        postJson("/api/encounters/{id}/history", Map.of("category", "SMOKING", "description", "Former smoker",
                "status", "FORMER"), encounterId)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/encounters/{id}/chart", encounterId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounter.id").value(encounterId.toString()))
                .andExpect(jsonPath("$.patient.id").value(patientId.toString()))
                .andExpect(jsonPath("$.symptoms", hasSize(2)))
                .andExpect(jsonPath("$.vitalSigns", hasSize(4)))
                .andExpect(jsonPath("$.labs", hasSize(2)))
                .andExpect(jsonPath("$.medications", hasSize(1)))
                .andExpect(jsonPath("$.allergies", hasSize(1)))
                .andExpect(jsonPath("$.history", hasSize(1)));

        Integer created = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where encounter_id = ? and event_type = 'CLINICAL_DATA_CREATED'",
                Integer.class, encounterId);
        assertThat(created).isEqualTo(8);
    }

    @Test
    void rejectsImplausibleVitalSignsAndDuplicatesInASet() throws Exception {
        postJson("/api/encounters/{id}/observations", Map.of("measurements",
                List.of(Map.of("type", "HEART_RATE", "value", 900))), encounterId)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("plausible range")));
        postJson("/api/encounters/{id}/observations", Map.of("measurements", List.of(
                Map.of("type", "HEART_RATE", "value", 90), Map.of("type", "HEART_RATE", "value", 91))), encounterId)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aiInferredDataRequiresExplicitClinicianConfirmation() throws Exception {
        postJson("/api/encounters/{id}/history", Map.of("category", "SMOKING", "description", "Smoker",
                "source", "AI_INFERRED"), encounterId)
                .andExpect(status().isUnprocessableContent());

        postJson("/api/encounters/{id}/history", Map.of("category", "SMOKING", "description", "Current smoker",
                "status", "CURRENT", "aiProposalReference", "assistant-message:synthetic#1"), encounterId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provenance.source").value("AI_INFERRED"))
                .andExpect(jsonPath("$.provenance.confirmedBy").value("clinician-test-1"));
    }

    @Test
    void enteredInErrorRecordsAreHiddenByDefaultButKept() throws Exception {
        String created = postJson("/api/encounters/{id}/symptoms", Map.of("symptom", "Headache", "presence", "PRESENT"),
                encounterId).andReturn().getResponse().getContentAsString();
        String symptomId = jsonMapper.readTree(created).get("id").asString();

        postJson("/api/encounters/{id}/symptoms/{sid}/entered-in-error", Map.of("reason", "Wrong patient"),
                encounterId, symptomId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enteredInError").value(true));

        mockMvc.perform(get("/api/encounters/{id}/symptoms", encounterId).with(clinician()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/encounters/{id}/symptoms", encounterId).param("includeEnteredInError", "true")
                        .with(clinician()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void familyHistoryRequiresRelationship() throws Exception {
        postJson("/api/encounters/{id}/history", Map.of("category", "FAMILY_HISTORY", "description", "Heart disease"),
                encounterId)
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void completedEncounterRejectsNewClinicalData() throws Exception {
        mockMvc.perform(post("/api/encounters/{id}/complete", encounterId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        postJson("/api/encounters/{id}/symptoms", Map.of("symptom", "Cough", "presence", "PRESENT"), encounterId)
                .andExpect(status().isUnprocessableContent());
    }
}
