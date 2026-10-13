package com.medassist.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.DifferentialCandidate;
import com.medassist.diagnosis.differential.DifferentialRanker;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.RankedDifferential;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.diagnosis.reasoning.ValidatedReasoning;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class DifferentialRankerTest {

    private static ValidatedReasoning.Differential differential(String name, ConfidenceLevel confidence, int supported) {
        List<ValidatedReasoning.Statement> statements = java.util.stream.IntStream.range(0, supported)
                .mapToObj(i -> new ValidatedReasoning.Statement("s" + i, List.of("F1"), List.of(), SupportStatus.SUPPORTED))
                .toList();
        return new ValidatedReasoning.Differential(name, null, null, ConceptVerificationStatus.UNVERIFIED, confidence,
                confidence, "why", statements, List.of(), List.of(), List.of(), null, "summary", List.of("K1"),
                GroundingStatus.GROUNDED, List.of());
    }

    @Test
    void ordersByConfidenceThenSupportedFindingsAndAppendsUncoveredKnowledgeCandidates() {
        ValidatedReasoning reasoning = new ValidatedReasoning(List.of(), List.of(
                differential("Condition A", ConfidenceLevel.LOW, 5),
                differential("Condition B", ConfidenceLevel.HIGH, 1),
                differential("Condition C", ConfidenceLevel.HIGH, 3)), List.of(), List.of());
        List<DifferentialCandidate> candidates = List.of(
                new DifferentialCandidate("condition b", List.of("K1"), 0.8),
                new DifferentialCandidate("Condition D", List.of("K2", "K3"), 0.7));

        List<RankedDifferential> ranked = new DifferentialRanker().rank(reasoning, candidates);

        assertThat(ranked).extracting(RankedDifferential::conditionName)
                .containsExactly("Condition C", "Condition B", "Condition A", "Condition D");
        assertThat(ranked).extracting(RankedDifferential::rank).containsExactly(1, 2, 3, 4);
        assertThat(ranked.get(3).ai()).isNull();
        assertThat(ranked.get(3).rankBasis()).contains("not assessed by AI");
    }

    @Test
    void withoutAiOnlyKnowledgeLinkedCandidatesAreListed() {
        List<RankedDifferential> ranked = new DifferentialRanker().rank(null,
                List.of(new DifferentialCandidate("Condition X", List.of("K1"), 0.5)));
        assertThat(ranked).singleElement().satisfies(r -> assertThat(r.knowledgeLinked()).isNotNull());
    }
}
