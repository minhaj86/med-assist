package com.medassist.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.diagnosis.differential.DifferentialCandidate;
import com.medassist.diagnosis.differential.KnowledgeLinkedCandidateGenerator;
import com.medassist.knowledge.citation.CitationValidator;
import com.medassist.knowledge.embedding.LocalHashingEmbeddingService;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmbeddingAndCitationTest {

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    @Test
    void localHashingEmbeddingsAreDeterministicNormalizedAndLexicallyMeaningful() {
        LocalHashingEmbeddingService service = new LocalHashingEmbeddingService();
        List<float[]> v = service.embed(List.of("central chest pain radiating to the left arm with sweating",
                "chest pain radiating to arm", "productive cough and fever for four days"));
        assertThat(v.getFirst()).hasSize(LocalHashingEmbeddingService.DIMENSIONS);
        assertThat(cosine(v.get(0), v.get(0))).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        assertThat(cosine(v.get(0), v.get(1))).isGreaterThan(cosine(v.get(0), v.get(2)));
        assertThat(service.embed(List.of("chest pain radiating to arm")).getFirst()).containsExactly(v.get(1));
    }

    @Test
    void citationValidatorOnlyAcceptsProvidedEvidenceKeys() {
        CitationValidator validator = new CitationValidator();
        CitationValidator.CitationCheck check = validator.check(
                List.of("F1", "k2", "K9", "F99", "Smith et al. 2019", "https://example.org/guideline", ""),
                Set.of("F1", "F2"), Set.of("K1", "K2"));
        assertThat(check.validFactKeys()).containsExactly("F1");
        assertThat(check.validEvidenceKeys()).containsExactly("K2");
        assertThat(check.invalid()).containsExactly("K9", "F99", "Smith et al. 2019", "https://example.org/guideline", "");
        assertThat(validator.check(null, Set.of(), Set.of()).hasAnyValid()).isFalse();
    }

    @Test
    void knowledgeLinkedCandidatesAreTraceableToEvidence() {
        RetrievedClinicalEvidence k1 = evidence("K1", 0.8, List.of("Condition A", "Condition B"));
        RetrievedClinicalEvidence k2 = evidence("K2", 0.6, List.of("condition a"));
        List<DifferentialCandidate> candidates = new KnowledgeLinkedCandidateGenerator().generate(List.of(k1, k2));
        assertThat(candidates.getFirst().conditionName()).isEqualTo("Condition A");
        assertThat(candidates.getFirst().evidenceKeys()).containsExactly("K1", "K2");
        assertThat(candidates).hasSize(2);
    }

    private static RetrievedClinicalEvidence evidence(String key, double similarity, List<String> tags) {
        return new RetrievedClinicalEvidence(key, UUID.randomUUID(), UUID.randomUUID(), "Doc", "Org", "S", 1,
                similarity, "text", "1", null, true, tags);
    }
}
