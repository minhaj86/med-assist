package com.medassist.support;

import static com.medassist.support.TestUsers.clinician;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class AssessmentTestSupport {

    private AssessmentTestSupport() {
    }

    /** Starts an assessment and waits for the run to finish; returns the final run JSON. */
    public static JsonNode runAssessment(MockMvc mvc, JsonMapper mapper, Object encounterId, boolean includeAi)
            throws Exception {
        String started = mvc.perform(post("/api/encounters/{id}/assessment", encounterId).with(clinician())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("includeAi", includeAi))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String runId = mapper.readTree(started).get("runId").asString();
        AtomicReference<JsonNode> last = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> {
            String body = mvc.perform(get("/api/assessment-runs/{id}", runId).with(clinician()))
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = mapper.readTree(body);
            last.set(node);
            String s = node.get("status").asString();
            return s.equals("COMPLETED") || s.equals("FAILED");
        });
        return last.get();
    }
}
