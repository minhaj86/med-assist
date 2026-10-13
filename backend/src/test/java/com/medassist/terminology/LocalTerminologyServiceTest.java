package com.medassist.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.common.json.JsonCodec;
import com.medassist.terminology.application.LocalTerminologyService;
import com.medassist.terminology.catalog.LocalConceptCatalog;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LocalTerminologyServiceTest {

    private final LocalTerminologyService service =
            new LocalTerminologyService(new LocalConceptCatalog(new JsonCodec(JsonMapper.builder().build())));

    @Test
    void normalizesSynonymsToCanonicalDisplayNamesWithoutInventingCodes() {
        ClinicalConcept concept = service.normalize("  Dyspnoea ", ConceptDomain.SYMPTOM);
        assertThat(concept.displayName()).isEqualTo("Shortness of breath");
        assertThat(concept.code()).isNull();
        assertThat(concept.verificationStatus()).isEqualTo(ConceptVerificationStatus.UNVERIFIED);
    }

    @Test
    void keepsUnknownTextAsUnverifiedConcept() {
        ClinicalConcept concept = service.normalize("Itchy left elbow", ConceptDomain.SYMPTOM);
        assertThat(concept.displayName()).isEqualTo("Itchy left elbow");
        assertThat(concept.isCoded()).isFalse();
    }

    @Test
    void onlyCuratedVitalSignCodesAreTreatedAsReferenceCodes() {
        ClinicalConcept heartRate = service.verify(CodingSystem.LOINC, "8867-4", "Heart rate");
        assertThat(heartRate.verificationStatus()).isEqualTo(ConceptVerificationStatus.CURATED_REFERENCE);

        ClinicalConcept unknown = service.verify(CodingSystem.SNOMED_CT, "123456", "Something");
        assertThat(unknown.verificationStatus()).isEqualTo(ConceptVerificationStatus.PROVIDED_UNVERIFIED);

        ClinicalConcept wrongSystem = service.verify(CodingSystem.SNOMED_CT, "8867-4", "Heart rate");
        assertThat(wrongSystem.verificationStatus()).isEqualTo(ConceptVerificationStatus.PROVIDED_UNVERIFIED);
    }

    @Test
    void conditionCatalogShipsNoCodes() {
        assertThat(service.suggest("", ConceptDomain.CONDITION, 100)).allSatisfy(c -> assertThat(c.code()).isNull());
        assertThat(service.suggest("", ConceptDomain.SYMPTOM, 100)).allSatisfy(c -> assertThat(c.code()).isNull());
    }

    @Test
    void conceptInvariantsPreventUncodedVerifiedConcepts() {
        assertThatThrownBy(() -> new ClinicalConcept(null, null, "x", ConceptVerificationStatus.CURATED_REFERENCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClinicalConcept(null, "123", "x", ConceptVerificationStatus.PROVIDED_UNVERIFIED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
