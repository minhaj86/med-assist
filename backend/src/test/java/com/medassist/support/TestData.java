package com.medassist.support;

import static com.medassist.support.TestUsers.clinician;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates fictional (synthetic) patients and encounters through the public API. */
public final class TestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private TestData() {
    }

    public static UUID createPatient(MockMvc mvc, JsonMapper mapper) throws Exception {
        String externalId = "SYN-IT-" + UUID.randomUUID().toString().substring(0, 8) + "-" + SEQUENCE.incrementAndGet();
        String body = mapper.writeValueAsString(Map.of(
                "externalPatientId", externalId,
                "firstName", "Synthia",
                "lastName", "Testpatient",
                "dateOfBirth", "1970-03-14",
                "biologicalSex", "FEMALE"));
        String response = mvc.perform(post("/api/patients").with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).get("id").asString());
    }

    public static UUID createEncounter(MockMvc mvc, JsonMapper mapper, UUID patientId, String chiefComplaint)
            throws Exception {
        String body = mapper.writeValueAsString(Map.of("encounterType", "EMERGENCY", "chiefComplaint", chiefComplaint));
        String response = mvc.perform(post("/api/patients/{id}/encounters", patientId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(response);
        return UUID.fromString(node.get("id").asString());
    }
}
