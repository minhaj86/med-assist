package com.medassist.patient;

import static com.medassist.support.TestUsers.admin;
import static com.medassist.support.TestUsers.clinician;
import static com.medassist.support.TestUsers.knowledgeManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.TestData;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PatientApiIT extends AbstractIntegrationTest {

    @Test
    void registersViewsAndAuditsPatient() throws Exception {
        String body = json(Map.of("externalPatientId", "SYN-PAT-" + UUID.randomUUID().toString().substring(0, 8),
                "firstName", "Ada", "lastName", "Synthetic", "dateOfBirth", "1968-05-01",
                "biologicalSex", "FEMALE", "gender", "Woman"));

        String created = mockMvc.perform(post("/api/patients").with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/patients/")))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.synthetic").value(false))
                .andExpect(jsonPath("$.provenance").value("CLINICIAN"))
                .andExpect(jsonPath("$.ageYears").value(notNullValue()))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(jsonMapper.readTree(created).get("id").asString());

        mockMvc.perform(get("/api/patients/{id}", id).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Synthetic"));

        Integer audits = jdbcTemplate.queryForObject(
                "select count(*) from audit_events where patient_id = ? and event_type in ('PATIENT_CREATED','PATIENT_VIEWED')",
                Integer.class, id);
        assertThat(audits).isEqualTo(2);
        String metadata = jdbcTemplate.queryForObject(
                "select metadata::text from audit_events where patient_id = ? and event_type = 'PATIENT_CREATED'",
                String.class, id);
        assertThat(metadata).doesNotContain("Ada").doesNotContain("Synthetic").doesNotContain("1968");
    }

    @Test
    void validationErrorsAreReturnedAsProblemDetails() throws Exception {
        String body = json(Map.of("externalPatientId", "x", "firstName", "", "biologicalSex", "FEMALE"));
        mockMvc.perform(post("/api/patients").with(clinician()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.type").value("urn:medassist:problem:validation-error"))
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                .andExpect(jsonPath("$.errors[*].field", hasItem("externalPatientId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("lastName")))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void duplicateExternalIdentifierIsAConflict() throws Exception {
        String externalId = "SYN-DUP-" + UUID.randomUUID().toString().substring(0, 8);
        String body = json(Map.of("externalPatientId", externalId, "firstName", "A", "lastName", "B",
                "dateOfBirth", "1980-01-01", "biologicalSex", "MALE"));
        mockMvc.perform(post("/api/patients").with(clinician()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/patients").with(clinician()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:medassist:problem:conflict"));
    }

    @Test
    void searchMatchesNameAndEscapesWildcards() throws Exception {
        UUID id = TestData.createPatient(mockMvc, jsonMapper);
        mockMvc.perform(get("/api/patients").param("query", "testpat").with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id", hasItem(id.toString())));
        mockMvc.perform(get("/api/patients").param("query", "%").with(clinician()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void updatesDemographics() throws Exception {
        UUID id = TestData.createPatient(mockMvc, jsonMapper);
        String body = json(Map.of("firstName", "Synthia", "lastName", "Renamed", "dateOfBirth", "1970-03-14",
                "biologicalSex", "FEMALE"));
        mockMvc.perform(put("/api/patients/{id}", id).with(clinician()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Renamed"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:medassist:problem:unauthenticated"));
    }

    @Test
    void nonClinicalRolesCannotAccessPatientData() throws Exception {
        mockMvc.perform(get("/api/patients").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("urn:medassist:problem:forbidden"));
        mockMvc.perform(get("/api/patients").with(knowledgeManager()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPatientIsNotFound() throws Exception {
        mockMvc.perform(get("/api/patients/{id}", UUID.randomUUID()).with(clinician()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:medassist:problem:not-found"));
    }
}
