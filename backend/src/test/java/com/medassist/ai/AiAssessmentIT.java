package com.medassist.ai;

import static com.medassist.support.TestUsers.admin;
import static com.medassist.support.TestUsers.clinician;
import static com.medassist.support.TestUsers.knowledgeManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.ai.provider.AiProviderException;
import com.medassist.ai.provider.ClinicalReasoningRequest;
import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.AiFixtures;
import com.medassist.support.AssessmentTestSupport;
import com.medassist.support.ScriptedClinicalAiProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/** AI Case A end-to-end with a scripted provider: structure, evidence linkage, validation and audit. */
class AiAssessmentIT extends AbstractIntegrationTest {

    @Autowired
    ScriptedClinicalAiProvider ai;

    private UUID patientId;
    private UUID encounterId;
    private String externalId;

    @BeforeEach
    void setUp() throws Exception {
        ai.reset();
        ensureChestPainKnowledge();
        externalId = "SYN-CASEA-" + UUID.randomUUID().toString().substring(0, 6);
        String patient = mockMvc.perform(post("/api/patients").with(clinician()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("externalPatientId", externalId, "firstName", "Casey", "lastName", "Simulated",
                                "dateOfBirth", java.time.LocalDate.now().minusYears(56).minusDays(10).toString(),
                                "biologicalSex", "MALE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        patientId = UUID.fromString(jsonMapper.readTree(patient).get("id").asString());
        String encounter = mockMvc.perform(post("/api/patients/{id}/encounters", patientId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("encounterType", "EMERGENCY",
                                "chiefComplaint", "Chest pain and breathlessness (call Casey on 07700 900123)"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        encounterId = UUID.fromString(jsonMapper.readTree(encounter).get("id").asString());
        postData("/api/encounters/{id}/symptoms", Map.of("symptom", "Chest pain", "presence", "PRESENT",
                "onsetPattern", "SUDDEN", "severity", 7, "character", "pressure-like", "radiation", "left arm"));
        postData("/api/encounters/{id}/symptoms", Map.of("symptom", "Shortness of breath", "presence", "PRESENT"));
        postData("/api/encounters/{id}/symptoms", Map.of("symptom", "Fever", "presence", "ABSENT"));
        postData("/api/encounters/{id}/observations", Map.of("measurements", List.of(
                Map.of("type", "SYSTOLIC_BLOOD_PRESSURE", "value", 135), Map.of("type", "HEART_RATE", "value", 128),
                Map.of("type", "RESPIRATORY_RATE", "value", 24), Map.of("type", "OXYGEN_SATURATION", "value", 93))));
    }

    private void postData(String url, Object body) throws Exception {
        mockMvc.perform(post(url, encounterId).with(clinician()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
    }

    private void ensureChestPainKnowledge() throws Exception {
        Integer existing = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_documents where title = 'Synthetic AI test chest pain note' and ingestion_status = 'INDEXED'",
                Integer.class);
        if (existing != null && existing > 0) {
            return;
        }
        String markdown = "# Synthetic chest pain note\n\nSynthetic test content. Chest pain radiating to the left arm with "
                + "breathlessness, sudden pressure-like chest pain, shortness of breath. ECG and troponin are discussed.";
        MockMultipartFile metadata = new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, json(Map.of(
                "title", "Synthetic AI test chest pain note", "documentType", "SYNTHETIC_DEMO",
                "sourceOrganization", "Integration test (synthetic)", "version", "1", "approvalStatus", "APPROVED",
                "conditionTags", List.of("Acute coronary syndrome", "Pulmonary embolism"))).getBytes(StandardCharsets.UTF_8));
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/admin/knowledge/documents").file(metadata)
                        .file(new MockMultipartFile("file", "chest.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8)))
                        .with(knowledgeManager()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(body).get("id").asString();
        await().atMost(Duration.ofSeconds(30)).until(() -> "INDEXED".equals(jdbcTemplate.queryForObject(
                "select ingestion_status from knowledge_documents where id = ?::uuid", String.class, id)));
    }

    @Test
    void caseAValidatedAiReasoningIsLinkedToEvidenceAndAudited() throws Exception {
        ai.enqueue(AiFixtures.caseAResponse());
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, true);
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(run.get("stages").toString()).contains("GENERATING_REASONING").contains("VALIDATING_RESPONSE");
        String versionId = run.get("versionId").asString();

        String body = mockMvc.perform(get("/api/assessments/{id}", versionId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.status").value("COMPLETED"))
                .andExpect(jsonPath("$.evidence[0].evidenceKey").value("K1"))
                .andExpect(jsonPath("$.aiRedFlags", hasSize(2)))
                .andExpect(jsonPath("$.aiRedFlags[0].origin").value("AI"))
                .andExpect(jsonPath("$.aiRedFlags[0].reviewState").value("AI_GENERATED"))
                .andExpect(jsonPath("$.differentials[0].conditionName").value("Acute coronary syndrome"))
                .andExpect(jsonPath("$.differentials[0].method").value("AI_REASONED"))
                .andExpect(jsonPath("$.differentials[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.differentials[0].code").doesNotExist())
                .andExpect(jsonPath("$.differentials[0].codeVerification").value("UNVERIFIED"))
                .andExpect(jsonPath("$.differentials[0].citations[0]").value("K1"))
                .andExpect(jsonPath("$.differentials[0].supportingEvidence[0].factKeys[0]").value("F3"))
                .andExpect(jsonPath("$.differentials[0].contradictingEvidence[0].support").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.differentials[0].suggestedInvestigations[0].proposedBy").value("AI"))
                .andExpect(jsonPath("$.differentials[1].conditionName").value("Pulmonary embolism"))
                .andExpect(jsonPath("$.differentials[1].confidence").value("LOW"))
                .andExpect(jsonPath("$.differentials[1].originalConfidence").value("MODERATE"))
                .andExpect(jsonPath("$.differentials[1].grounding").value("UNGROUNDED"))
                .andExpect(jsonPath("$.differentials[2].supportingEvidence[0].support").value("WITHHELD"))
                .andExpect(jsonPath("$.uncertainties[0]").value(org.hamcrest.Matchers.startsWith("Insufficient information")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Smith et al").doesNotContain("definitely").doesNotContain("394659003");

        ClinicalReasoningRequest request = ai.requests().getFirst();
        assertThat(request.temperature()).isLessThanOrEqualTo(0.2);
        assertThat(request.promptId()).isEqualTo("clinical-reasoning");
        assertThat(request.userPrompt()).contains("F3: Chest pain: present").contains("K1 [source:")
                .doesNotContain("Casey").doesNotContain("Simulated").doesNotContain(externalId)
                .doesNotContain("07700 900123");

        String executionId = jsonMapper.readTree(body).get("ai").get("executionId").asString();
        Map<String, Object> execution = jdbcTemplate.queryForMap("select * from ai_executions where id = ?::uuid", executionId);
        assertThat(execution.get("status")).isEqualTo("SUCCEEDED");
        assertThat(execution.get("prompt_id")).isEqualTo("clinical-reasoning");
        assertThat(execution.get("prompt_version")).isEqualTo("1.0.0");
        assertThat(execution.get("provider")).isEqualTo("scripted-test");
        assertThat(execution.get("reported_model")).isEqualTo("scripted-model-v1-2026");
        assertThat(execution.get("retrieved_evidence").toString()).contains("K1");
        String audit = jdbcTemplate.queryForObject("select metadata::text from audit_events where entity_id = ? "
                + "and event_type = 'AI_PROMPT_EXECUTED'", String.class, executionId);
        assertThat(audit).contains("\"promptVersion\": \"1.0.0\"").contains("scripted-model-v1");

        JsonNode version = jsonMapper.readTree(body);
        String differentialId = version.get("differentials").get(0).get("id").asString();
        mockMvc.perform(post("/api/assessments/{id}/feedback", versionId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("suggestionType", "DIFFERENTIAL", "suggestionId", differentialId,
                                "action", "ACCEPTED", "reason", "Consistent with clinical findings (synthetic)"))))
                .andExpect(status().isCreated());
        String investigationId = version.get("differentials").get(0).get("suggestedInvestigations").get(0).get("id").asString();
        mockMvc.perform(post("/api/assessments/{id}/feedback", versionId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("suggestionType", "INVESTIGATION", "suggestionId", investigationId,
                                "action", "ACCEPTED"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/assessments/{id}", versionId).with(clinician()))
                .andExpect(jsonPath("$.differentials[0].reviewState").value("ACCEPTED"))
                .andExpect(jsonPath("$.differentials[0].latestFeedback.action").value("ACCEPTED"));
        mockMvc.perform(get("/api/encounters/{id}/investigations", encounterId).with(clinician()))
                .andExpect(jsonPath("$[0].name").value("12-lead ECG"))
                .andExpect(jsonPath("$[0].proposedBy").value("AI"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    void malformedOutputIsRetriedOnceThenRejectedWithoutShowingAiContent() throws Exception {
        ai.enqueue("Sure! The diagnosis is definitely a heart attack.");
        ai.enqueue("{\"redFlags\": []}");
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, true);
        assertThat(ai.requests()).hasSize(2);
        assertThat(ai.requests().get(1).userPrompt()).contains("did not match the required JSON schema");
        mockMvc.perform(get("/api/assessments/{id}", run.get("versionId").asString()).with(clinician()))
                .andExpect(jsonPath("$.ai.status").value("OUTPUT_REJECTED"))
                .andExpect(jsonPath("$.aiRedFlags", hasSize(0)))
                .andExpect(jsonPath("$.safetyAlerts[0].origin").value("CLINICAL_SCORE"))
                .andExpect(jsonPath("$.safetyAlerts[*].origin").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not("AI"))))
                .andExpect(jsonPath("$.ai.message").value(org.hamcrest.Matchers.containsString("did not pass validation")));
        Integer rejected = jdbcTemplate.queryForObject("select count(*) from audit_events where encounter_id = ? "
                + "and event_type = 'AI_RESPONSE_REJECTED'", Integer.class, encounterId);
        assertThat(rejected).isEqualTo(1);
    }

    @Test
    void repairedSecondAttemptIsAccepted() throws Exception {
        ai.enqueue("not json");
        ai.enqueue(AiFixtures.caseAResponse());
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, true);
        mockMvc.perform(get("/api/assessments/{id}", run.get("versionId").asString()).with(clinician()))
                .andExpect(jsonPath("$.ai.status").value("COMPLETED"));
        Integer attempts = jdbcTemplate.queryForObject("select attempts from ai_executions where encounter_id = ? "
                + "and status = 'SUCCEEDED'", Integer.class, encounterId);
        assertThat(attempts).isEqualTo(2);
    }

    @Test
    void providerOutageDegradesGracefullyAndRulesStillApply() throws Exception {
        ai.enqueueFailure(AiProviderException.Category.UNAVAILABLE);
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, true);
        assertThat(run.get("status").asString()).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/assessments/{id}", run.get("versionId").asString()).with(clinician()))
                .andExpect(jsonPath("$.ai.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.safetyAlerts[*].origin").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.not("AI"))))
                .andExpect(jsonPath("$.differentials[0].method").value("KNOWLEDGE_LINKED"));
        mockMvc.perform(get("/api/patients/{id}", patientId).with(clinician())).andExpect(status().isOk());
    }

    @Test
    void assistantAnswersWithCitationsAndOnlyProposesUpdates() throws Exception {
        ai.enqueue("""
                {"answer": "Pulmonary embolism may be considered because of breathlessness with tachycardia (F4, F7).",
                 "statements": [{"statement": "Breathlessness and tachycardia are recorded", "evidenceIds": ["F4", "F7"]},
                                {"statement": "An invented study supports this", "evidenceIds": ["K42"]}],
                 "insufficientInformation": false,
                 "proposedUpdates": [{"category": "HISTORY", "description": "Recent long-haul flight",
                                      "rationale": "Mentioned in the clinician's question"}],
                 "followUpQuestions": ["Any leg swelling?"]}""");
        int historyBefore = jdbcTemplate.queryForObject("select count(*) from medical_history where patient_id = ?",
                Integer.class, patientId);
        mockMvc.perform(post("/api/encounters/{id}/assistant/questions", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", "Why is pulmonary embolism being considered? He flew from Sydney last week."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.statements[0].support").value("SUPPORTED"))
                .andExpect(jsonPath("$.statements[1].support").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.proposedUpdates[0].category").value("HISTORY"))
                .andExpect(jsonPath("$.proposedUpdates[0].proposalReference").value(org.hamcrest.Matchers.startsWith("assistant-execution:")));
        int historyAfter = jdbcTemplate.queryForObject("select count(*) from medical_history where patient_id = ?",
                Integer.class, patientId);
        assertThat(historyAfter).isEqualTo(historyBefore);

        ai.enqueueFailure(AiProviderException.Category.TIMEOUT);
        mockMvc.perform(post("/api/encounters/{id}/assistant/questions", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("question", "Any other ideas?"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("continue to work")));
        mockMvc.perform(get("/api/encounters/{id}/assistant/conversation", encounterId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("CLINICIAN"))
                .andExpect(jsonPath("$[0].conversationId").isNotEmpty());
    }

    @Test
    void adminSeesAiConfigurationWithoutSecrets() throws Exception {
        String body = mockMvc.perform(get("/api/admin/ai/status").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("scripted-test"))
                .andExpect(jsonPath("$.activePrompts[*].promptId").value(org.hamcrest.Matchers.hasItem("clinical-reasoning")))
                .andExpect(jsonPath("$.embeddingModel").value("local-hashing-v1:384"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body.toLowerCase()).doesNotContain("api_key").doesNotContain("sk-");
        mockMvc.perform(get("/api/admin/ai/status").with(clinician())).andExpect(status().isForbidden());
    }
}
