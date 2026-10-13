package com.medassist.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.ai.guardrail.SafetyGuardrail;
import com.medassist.ai.prompt.OutputSchemaRegistry;
import com.medassist.ai.validation.ReasoningOutputContract;
import com.medassist.ai.validation.ReasoningValidator;
import com.medassist.ai.validation.StructuredOutputParser;
import com.medassist.common.domain.ClinicalSeverity;
import com.medassist.common.json.JsonCodec;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.diagnosis.reasoning.ValidatedReasoning;
import com.medassist.knowledge.citation.CitationValidator;
import com.medassist.support.AiFixtures;
import com.medassist.terminology.application.LocalTerminologyService;
import com.medassist.terminology.catalog.LocalConceptCatalog;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import jakarta.validation.Validation;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI structure test "Case A" (56-year-old simulated patient: chest pain, dyspnoea, tachycardia). Tests the output
 * structure and the deterministic validation layer - NOT medical correctness.
 */
class ReasoningValidatorTest {

    private static final Set<String> FACTS = Set.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9");
    private static final Set<String> EVIDENCE = Set.of("K1", "K2");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReasoningValidator validator = new ReasoningValidator(new CitationValidator(), new SafetyGuardrail(),
            new LocalTerminologyService(new LocalConceptCatalog(new JsonCodec(mapper))));

    private ReasoningValidator.Result validateCaseA() {
        ReasoningOutputContract contract = new StructuredOutputParser(mapper,
                Validation.buildDefaultValidatorFactory().getValidator(), new OutputSchemaRegistry())
                .parse(AiFixtures.caseAResponse(), "clinical-reasoning-output.v1", ReasoningOutputContract.class).value();
        return validator.validate(contract, FACTS, EVIDENCE);
    }

    @Test
    void redFlagsKeepSeverityAndLinkOnlyToProvidedEvidence() {
        ValidatedReasoning reasoning = validateCaseA().reasoning();
        assertThat(reasoning.redFlags()).hasSize(2);
        ValidatedReasoning.RedFlag first = reasoning.redFlags().getFirst();
        assertThat(first.severity()).isEqualTo(ClinicalSeverity.URGENT);
        assertThat(first.factKeys()).containsExactly("F7", "F9");
        assertThat(first.support()).isEqualTo(SupportStatus.SUPPORTED);
        ValidatedReasoning.RedFlag second = reasoning.redFlags().get(1);
        assertThat(second.evidenceKeys()).containsExactly("K1");
        assertThat(validateCaseA().issues()).anySatisfy(i -> assertThat(i).contains("Invalid citation"));
    }

    @Test
    void unverifiableCodesAreRemovedAndStatementsWithoutCitationsAreUnsupported() {
        ValidatedReasoning.Differential acs = validateCaseA().reasoning().differentials().getFirst();
        assertThat(acs.conditionName()).isEqualTo("Acute coronary syndrome");
        assertThat(acs.code()).isNull();
        assertThat(acs.codeVerification()).isEqualTo(ConceptVerificationStatus.UNVERIFIED);
        assertThat(acs.validationNotes()).anySatisfy(n -> assertThat(n).contains("could not be verified"));
        assertThat(acs.supportingEvidence()).allSatisfy(s -> assertThat(s.support()).isEqualTo(SupportStatus.SUPPORTED));
        assertThat(acs.contradictingEvidence().getFirst().support()).isEqualTo(SupportStatus.UNSUPPORTED);
        assertThat(acs.grounding()).isEqualTo(GroundingStatus.PARTIALLY_GROUNDED);
        assertThat(acs.confidence()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(acs.citations()).containsExactly("K1");
    }

    @Test
    void fabricatedCitationsLeaveDifferentialUngroundedWithConfidenceCapped() {
        ValidatedReasoning.Differential pe = validateCaseA().reasoning().differentials().get(1);
        assertThat(pe.citations()).isEmpty();
        assertThat(pe.grounding()).isEqualTo(GroundingStatus.UNGROUNDED);
        assertThat(pe.originalConfidence()).isEqualTo(ConfidenceLevel.MODERATE);
        assertThat(pe.confidence()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(pe.validationNotes()).anySatisfy(n -> assertThat(n).contains("Confidence capped"));
    }

    @Test
    void fabricatedReferencesAndDefinitiveLanguageAreWithheld() {
        ValidatedReasoning.Differential msk = validateCaseA().reasoning().differentials().get(2);
        ValidatedReasoning.Statement fabricated = msk.supportingEvidence().getFirst();
        assertThat(fabricated.support()).isEqualTo(SupportStatus.WITHHELD);
        assertThat(fabricated.text()).doesNotContain("Smith").startsWith("[Withheld by the safety validator");
        assertThat(msk.reasoningSummary()).doesNotContain("definitely").contains("Withheld");
        ReasoningValidator.ValidationSummary summary = validateCaseA().summary();
        assertThat(summary.withheldItems()).isGreaterThanOrEqualTo(2);
        assertThat(summary.removedCodes()).isEqualTo(1);
        assertThat(summary.cappedConfidences()).isEqualTo(1);
    }

    @Test
    void insufficientInformationWordingIsPreserved() {
        assertThat(validateCaseA().reasoning().uncertainties())
                .anySatisfy(u -> assertThat(u).startsWith("Insufficient information to reliably assess this possibility"));
    }
}
