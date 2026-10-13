package com.medassist.knowledge;

import static com.medassist.support.TestUsers.clinician;
import static com.medassist.support.TestUsers.knowledgeManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.AssessmentTestSupport;
import com.medassist.support.TestData;
import com.medassist.support.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

class KnowledgeBaseIT extends AbstractIntegrationTest {

    private MockMultipartFile metadata(String title, String version, List<String> tags) {
        return new MockMultipartFile("metadata", "", MediaType.APPLICATION_JSON_VALUE, json(Map.of(
                "title", title, "documentType", "SYNTHETIC_DEMO", "sourceOrganization", "Integration test (synthetic)",
                "version", version, "approvalStatus", "APPROVED", "conditionTags", tags,
                "publicationDate", "2026-01-01")).getBytes(StandardCharsets.UTF_8));
    }

    private String upload(MockMultipartFile metadata, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/knowledge/documents").file(metadata).file(file)
                        .with(knowledgeManager()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ingestionStatus").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("id").asString();
    }

    private JsonNode awaitIndexed(String id) {
        final JsonNode[] last = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            String body = mockMvc.perform(get("/api/admin/knowledge/documents/{id}", id).with(knowledgeManager()))
                    .andReturn().getResponse().getContentAsString();
            last[0] = jsonMapper.readTree(body);
            String s = last[0].get("summary").get("ingestionStatus").asString();
            return s.equals("INDEXED") || s.equals("FAILED");
        });
        return last[0];
    }

    @Test
    void ingestsSearchesGroundsAssessmentsAndCanBeRemoved() throws Exception {
        String markdown = """
                # Synthetic zebra fever note
                > Synthetic integration-test content, not clinical guidance.

                ## Presentation
                Zebrafever syndrome is a fictional condition used only in tests. It presents with striped rash and
                quivering fever. Patients describe a striped rash on the forearms.

                ## Investigations
                The fictional stripe assay is discussed as the distinguishing test for zebrafever syndrome.
                """;
        String id = upload(metadata("Synthetic zebrafever note", "1.0", List.of("Zebrafever syndrome")),
                new MockMultipartFile("file", "zebra.md", "text/markdown", markdown.getBytes(StandardCharsets.UTF_8)));

        JsonNode detail = awaitIndexed(id);
        assertThat(detail.get("summary").get("ingestionStatus").asString()).isEqualTo("INDEXED");
        assertThat(detail.get("summary").get("chunkCount").asInt()).isPositive();
        assertThat(detail.get("embeddingsCurrent").asBoolean()).isTrue();
        assertThat(detail.get("extractedText").asString()).contains("striped rash");

        mockMvc.perform(get("/api/admin/knowledge/documents/{id}/chunks", id).with(knowledgeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].metadata.title").value("Synthetic zebrafever note"))
                .andExpect(jsonPath("$.items[0].metadata.documentVersion").value("1.0"))
                .andExpect(jsonPath("$.items[*].section", hasItem("Presentation")));

        mockMvc.perform(post("/api/admin/knowledge/search").with(knowledgeManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "striped rash quivering fever", "topK", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(id))
                .andExpect(jsonPath("$[0].retrievalSimilarity").value(greaterThan(0.15)))
                .andExpect(jsonPath("$[0].usableForClinicalRetrieval").value(true));

        UUID patientId = TestData.createPatient(mockMvc, jsonMapper);
        UUID encounterId = TestData.createEncounter(mockMvc, jsonMapper, patientId, "Striped rash and quivering fever");
        mockMvc.perform(post("/api/encounters/{id}/symptoms", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("symptom", "Striped rash", "presence", "PRESENT", "location", "forearms"))))
                .andExpect(status().isCreated());
        JsonNode run = AssessmentTestSupport.runAssessment(mockMvc, jsonMapper, encounterId, false);
        String versionId = run.get("versionId").asString();
        mockMvc.perform(get("/api/assessments/{id}", versionId).with(clinician()))
                .andExpect(jsonPath("$.evidence[0].evidenceKey").value("K1"))
                .andExpect(jsonPath("$.evidence[0].documentId").value(id))
                .andExpect(jsonPath("$.evidence[0].syntheticSource").value(true))
                .andExpect(jsonPath("$.differentials[0].conditionName").value("Zebrafever syndrome"))
                .andExpect(jsonPath("$.differentials[0].method").value("KNOWLEDGE_LINKED"))
                .andExpect(jsonPath("$.differentials[0].confidence").doesNotExist());

        mockMvc.perform(patch("/api/admin/knowledge/documents/{id}", id).with(knowledgeManager())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.usableForClinicalRetrieval").value(false));
        String hits = mockMvc.perform(post("/api/admin/knowledge/search").with(knowledgeManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "striped rash quivering fever", "topK", 5))))
                .andReturn().getResponse().getContentAsString();
        assertThat(hits).doesNotContain(id);
        mockMvc.perform(post("/api/admin/knowledge/search").with(knowledgeManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("query", "striped rash quivering fever", "topK", 5, "includeInactive", true))))
                .andExpect(jsonPath("$[0].documentId").value(id))
                .andExpect(jsonPath("$[0].usableForClinicalRetrieval").value(false));

        mockMvc.perform(delete("/api/admin/knowledge/documents/{id}", id).with(knowledgeManager()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/knowledge/documents/{id}", id).with(knowledgeManager()))
                .andExpect(status().isNotFound());
        Integer chunks = jdbcTemplate.queryForObject("select count(*) from knowledge_chunks where document_id = ?::uuid",
                Integer.class, id);
        assertThat(chunks).isZero();
        mockMvc.perform(get("/api/assessments/{id}", versionId).with(clinician()))
                .andExpect(jsonPath("$.evidence[0].documentTitle").value("Synthetic zebrafever note"));
    }

    @Test
    void ingestsPdfWithPageNumbersAndSupersedesOnNewVersion() throws Exception {
        byte[] pdf = TestPdfs.twoPagePdf("SYNTHETIC PDF PAGE ONE about quokka cough", "Page two about quokka wheeze");
        String v1 = upload(metadata("Synthetic quokka PDF", "2025.1", List.of()),
                new MockMultipartFile("file", "quokka.pdf", "application/pdf", pdf));
        JsonNode detail = awaitIndexed(v1);
        assertThat(detail.get("pageCount").asInt()).isEqualTo(2);
        mockMvc.perform(get("/api/admin/knowledge/documents/{id}/chunks", v1).with(knowledgeManager()))
                .andExpect(jsonPath("$.items[0].pageNumber").value(1));

        byte[] pdf2 = TestPdfs.twoPagePdf("SYNTHETIC PDF v2 about quokka cough", "Updated page two");
        String body = mockMvc.perform(multipart("/api/admin/knowledge/documents/{id}/versions", v1)
                        .file(metadata("Synthetic quokka PDF", "2026.1", List.of()))
                        .file(new MockMultipartFile("file", "quokka-v2.pdf", "application/pdf", pdf2))
                        .with(knowledgeManager()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String v2 = jsonMapper.readTree(body).get("id").asString();
        awaitIndexed(v2);
        JsonNode old = awaitIndexed(v1);
        assertThat(old.get("summary").get("enabled").asBoolean()).isFalse();
        assertThat(old.get("summary").get("supersededById").asString()).isEqualTo(v2);
        assertThat(old.get("versions")).hasSize(2);
    }

    @Test
    void rejectsDuplicatesUnsupportedTypesAndNonKnowledgeRoles() throws Exception {
        byte[] content = "Synthetic duplicate content for testing only.".getBytes(StandardCharsets.UTF_8);
        String id = upload(metadata("Synthetic duplicate", "1", List.of()),
                new MockMultipartFile("file", "dup.txt", "text/plain", content));
        awaitIndexed(id);
        mockMvc.perform(multipart("/api/admin/knowledge/documents")
                        .file(metadata("Synthetic duplicate again", "1", List.of()))
                        .file(new MockMultipartFile("file", "dup2.txt", "text/plain", content))
                        .with(knowledgeManager()))
                .andExpect(status().isConflict());
        mockMvc.perform(multipart("/api/admin/knowledge/documents")
                        .file(metadata("Word file", "1", List.of()))
                        .file(new MockMultipartFile("file", "file.docx", "application/msword", new byte[] {1, 2, 3}))
                        .with(knowledgeManager()))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/api/admin/knowledge/documents").with(clinician()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/knowledge/status").with(knowledgeManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingModel").value("local-hashing-v1:384"));
    }
}
