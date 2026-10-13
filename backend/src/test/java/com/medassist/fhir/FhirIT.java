package com.medassist.fhir;

import static com.medassist.support.TestUsers.clinician;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import com.medassist.support.AbstractIntegrationTest;
import com.medassist.support.AiFixtures;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

class FhirIT extends AbstractIntegrationTest {

    private final FhirContext fhir = FhirContext.forR4Cached();

    private String bundle(String externalId) {
        return AiFixtures.read("fhir/synthetic-import-bundle.json").replace("SYN-FHIR-0001", externalId);
    }

    @Test
    void importsSyntheticBundleWithProvenanceAndExportsItBack() throws Exception {
        String externalId = "SYN-FHIR-" + UUID.randomUUID().toString().substring(0, 8);
        String result = mockMvc.perform(post("/api/fhir/import").with(clinician())
                        .contentType("application/fhir+json").content(bundle(externalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientCreated").value(true))
                .andExpect(jsonPath("$.imported.Encounter").value(1))
                .andExpect(jsonPath("$.imported['Observation (vital sign)']").value(2))
                .andExpect(jsonPath("$.imported['Observation (laboratory)']").value(1))
                .andExpect(jsonPath("$.imported.Condition").value(1))
                .andExpect(jsonPath("$.imported.MedicationRequest").value(1))
                .andExpect(jsonPath("$.imported.AllergyIntolerance").value(1))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = jsonMapper.readTree(result);
        assertThat(node.get("warnings").toString()).contains("[degF]").contains("Practitioner");
        String encounterId = node.get("encounterId").asString();
        String patientId = node.get("patientId").asString();

        mockMvc.perform(get("/api/encounters/{id}/chart", encounterId).with(clinician()))
                .andExpect(jsonPath("$.patient.synthetic").value(true))
                .andExpect(jsonPath("$.patient.biologicalSex").value("FEMALE"))
                .andExpect(jsonPath("$.vitalSigns", hasSize(2)))
                .andExpect(jsonPath("$.vitalSigns[0].provenance.source").value("FHIR"))
                .andExpect(jsonPath("$.labs[0].abnormalFlag").value("HIGH"))
                .andExpect(jsonPath("$.labs[0].abnormalFlagSource").value("REPORTED"))
                .andExpect(jsonPath("$.history[0].description").value("Asthma"))
                .andExpect(jsonPath("$.allergies[0].severity").value("MILD"));

        String exported = mockMvc.perform(get("/api/fhir/encounter/{id}", encounterId).with(clinician()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/fhir+json"))
                .andReturn().getResponse().getContentAsString();
        Bundle bundle = fhir.newJsonParser().parseResource(Bundle.class, exported);
        assertThat(bundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
        assertThat(bundle.getEntry()).extracting(e -> e.getResource().fhirType())
                .contains("Patient", "Encounter", "Observation", "MedicationRequest", "AllergyIntolerance");
        Observation heartRate = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance).map(Observation.class::cast)
                .filter(o -> o.getCode().getCodingFirstRep().getCode() != null
                        && o.getCode().getCodingFirstRep().getCode().equals("8867-4"))
                .findFirst().orElseThrow();
        assertThat(heartRate.getValueQuantity().getValue().intValue()).isEqualTo(112);

        String patientJson = mockMvc.perform(get("/api/fhir/patient/{id}", patientId).with(clinician()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Patient patient = fhir.newJsonParser().parseResource(Patient.class, patientJson);
        assertThat(patient.getIdentifierFirstRep().getValue()).isEqualTo(externalId);
        assertThat(patient.getMeta().getTag()).anySatisfy(t -> assertThat(t.getCode()).isEqualTo("synthetic-test-data"));

        mockMvc.perform(post("/api/fhir/import").with(clinician()).contentType("application/fhir+json")
                        .content(bundle(externalId)))
                .andExpect(jsonPath("$.patientCreated").value(false))
                .andExpect(jsonPath("$.patientId").value(patientId));
    }

    @Test
    void rejectsInvalidFhirAndBundlesWithoutExactlyOnePatient() throws Exception {
        mockMvc.perform(post("/api/fhir/import").with(clinician()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"Nonsense\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post("/api/fhir/import").with(clinician()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\": \"Bundle\", \"type\": \"collection\", \"entry\": []}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The import must contain exactly one Patient resource"));
    }
}
