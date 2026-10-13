package com.medassist.ai.validation;

import com.medassist.ai.guardrail.SafetyGuardrail;
import com.medassist.diagnosis.differential.ConfidenceLevel;
import com.medassist.diagnosis.differential.GroundingStatus;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.diagnosis.reasoning.ValidatedReasoning;
import com.medassist.knowledge.citation.CitationValidator;
import com.medassist.knowledge.citation.CitationValidator.CitationCheck;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import com.medassist.terminology.domain.TerminologyService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The deterministic validation layer between the model and the clinician. It cannot be bypassed: only its output
 * ({@link ValidatedReasoning}) is persisted or displayed.
 *
 * <ul>
 *   <li>Citations: only F-/K-keys actually provided are accepted; statements without a valid citation are marked
 *       UNSUPPORTED; invalid ids are dropped and reported.</li>
 *   <li>Safety language: definitive diagnoses, fabricated-looking references and numeric probabilities are
 *       withheld.</li>
 *   <li>Terminology: AI-proposed codes are kept only when a terminology source verifies them.</li>
 *   <li>Grounding: a differential without a valid knowledge citation is UNGROUNDED and its confidence is capped at
 *       LOW.</li>
 * </ul>
 */
@Component
public class ReasoningValidator {

    public record ValidationSummary(int differentials, int redFlags, int supportedStatements,
                                    int unsupportedStatements, int withheldItems, int removedCodes,
                                    int cappedConfidences, int invalidCitations, int droppedItems) {
    }

    public record Result(ValidatedReasoning reasoning, ValidationSummary summary, List<String> issues) {
    }

    private final CitationValidator citationValidator;
    private final SafetyGuardrail guardrail;
    private final TerminologyService terminologyService;

    public ReasoningValidator(CitationValidator citationValidator, SafetyGuardrail guardrail,
                              TerminologyService terminologyService) {
        this.citationValidator = citationValidator;
        this.guardrail = guardrail;
        this.terminologyService = terminologyService;
    }

    public Result validate(ReasoningOutputContract output, Set<String> factKeys, Set<String> evidenceKeys) {
        Counters c = new Counters();
        List<String> issues = new ArrayList<>();

        List<ValidatedReasoning.RedFlag> redFlags = new ArrayList<>();
        for (ReasoningOutputContract.RedFlag flag : output.redFlags()) {
            List<SafetyGuardrail.Finding> findings = findings(flag.title(), flag.reason());
            if (!findings.isEmpty()) {
                c.withheld++;
                issues.add("AI red flag withheld by safety validator (" + findings.getFirst().type() + ")");
                continue;
            }
            CitationCheck check = citationValidator.check(flag.evidenceIds(), factKeys, evidenceKeys);
            c.countInvalid(check, issues, "red flag");
            redFlags.add(new ValidatedReasoning.RedFlag(flag.title().strip(), flag.severity(), flag.reason().strip(),
                    check.validFactKeys(), check.validEvidenceKeys(),
                    check.hasAnyValid() ? SupportStatus.SUPPORTED : SupportStatus.UNSUPPORTED));
        }

        List<ValidatedReasoning.Differential> differentials = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ReasoningOutputContract.Differential d : output.differentialDiagnoses()) {
            List<String> notes = new ArrayList<>();
            List<SafetyGuardrail.Finding> nameFindings = guardrail.inspect(d.condition());
            if (!nameFindings.isEmpty()) {
                c.dropped++;
                issues.add("Differential dropped: condition name failed safety validation");
                continue;
            }
            ClinicalConcept concept = verifyConcept(d, notes, c);
            if (!seen.add(concept.displayName().toLowerCase(Locale.ROOT))) {
                c.dropped++;
                issues.add("Duplicate differential dropped: " + concept.displayName());
                continue;
            }
            List<ValidatedReasoning.Statement> supporting = statements(d.supportingEvidence(), factKeys, evidenceKeys, c, issues);
            List<ValidatedReasoning.Statement> contradicting = statements(d.contradictingEvidence(), factKeys, evidenceKeys, c, issues);
            List<String> missing = safeTexts(d.missingInformation(), c, issues);
            List<ValidatedReasoning.Investigation> investigations = new ArrayList<>();
            for (ReasoningOutputContract.Investigation i : d.suggestedInvestigations()) {
                List<SafetyGuardrail.Finding> findings = findings(i.name(), i.reason());
                if (!findings.isEmpty()) {
                    c.withheld++;
                    issues.add("Suggested investigation withheld by safety validator");
                    continue;
                }
                CitationCheck check = citationValidator.check(i.evidenceIds(), factKeys, evidenceKeys);
                c.countInvalid(check, issues, "investigation");
                investigations.add(new ValidatedReasoning.Investigation(i.name().strip(), i.reason().strip(),
                        Stream.concat(check.validFactKeys().stream(), check.validEvidenceKeys().stream()).toList(),
                        check.hasAnyValid() ? SupportStatus.SUPPORTED : SupportStatus.UNSUPPORTED));
            }
            CitationCheck citations = citationValidator.check(d.citations(), Set.of(), evidenceKeys);
            if (citations.hasInvalid()) {
                c.invalidCitations += citations.invalid().size();
                notes.add("Removed " + citations.invalid().size() + " citation(s) that did not match retrieved knowledge");
            }
            List<String> knowledgeCitations = new ArrayList<>(citations.validEvidenceKeys());
            Stream.concat(supporting.stream(), contradicting.stream())
                    .flatMap(s -> s.evidenceKeys().stream())
                    .filter(k -> !knowledgeCitations.contains(k))
                    .forEach(knowledgeCitations::add);
            boolean anyUnsupported = Stream.concat(supporting.stream(), contradicting.stream())
                    .anyMatch(s -> s.support() != SupportStatus.SUPPORTED);
            GroundingStatus grounding = knowledgeCitations.isEmpty() ? GroundingStatus.UNGROUNDED
                    : anyUnsupported ? GroundingStatus.PARTIALLY_GROUNDED : GroundingStatus.GROUNDED;
            ConfidenceLevel confidence = d.confidence();
            if (grounding == GroundingStatus.UNGROUNDED && confidence != ConfidenceLevel.LOW) {
                confidence = ConfidenceLevel.LOW;
                c.capped++;
                notes.add("Confidence capped at LOW: no validated knowledge citation supports this possibility");
            }
            differentials.add(new ValidatedReasoning.Differential(concept.displayName(), concept.system(), concept.code(),
                    concept.verificationStatus(), confidence, d.confidence(), safeText(d.whyConsidered(), c, issues),
                    supporting, contradicting, missing, investigations, safeText(d.distinguishingFeatures(), c, issues),
                    safeText(d.reasoningSummary(), c, issues), List.copyOf(knowledgeCitations), grounding,
                    List.copyOf(notes)));
        }

        ValidatedReasoning reasoning = new ValidatedReasoning(List.copyOf(redFlags), List.copyOf(differentials),
                safeTexts(output.uncertainties(), c, issues), safeTexts(output.additionalQuestions(), c, issues));
        return new Result(reasoning, new ValidationSummary(differentials.size(), redFlags.size(), c.supported,
                c.unsupported, c.withheld, c.removedCodes, c.capped, c.invalidCitations, c.dropped), List.copyOf(issues));
    }

    private ClinicalConcept verifyConcept(ReasoningOutputContract.Differential d, List<String> notes, Counters c) {
        ClinicalConcept normalized = terminologyService.normalize(d.condition(), ConceptDomain.CONDITION);
        if (d.code() == null || d.code().isBlank()) {
            return normalized;
        }
        ClinicalConcept verified = terminologyService.verify(d.codingSystem(), d.code(), normalized.displayName());
        if (verified.verificationStatus() == ConceptVerificationStatus.VERIFIED_TERMINOLOGY_SERVICE
                || verified.verificationStatus() == ConceptVerificationStatus.CURATED_REFERENCE) {
            return verified;
        }
        c.removedCodes++;
        notes.add("AI-proposed code could not be verified by a terminology source and was removed");
        return normalized;
    }

    private List<ValidatedReasoning.Statement> statements(List<ReasoningOutputContract.Statement> input,
                                                          Set<String> factKeys, Set<String> evidenceKeys,
                                                          Counters c, List<String> issues) {
        List<ValidatedReasoning.Statement> result = new ArrayList<>();
        for (ReasoningOutputContract.Statement s : input) {
            List<SafetyGuardrail.Finding> findings = guardrail.inspect(s.statement());
            if (!findings.isEmpty()) {
                c.withheld++;
                result.add(new ValidatedReasoning.Statement(SafetyGuardrail.withheldPlaceholder(findings), List.of(),
                        List.of(), SupportStatus.WITHHELD));
                continue;
            }
            CitationCheck check = citationValidator.check(s.evidenceIds(), factKeys, evidenceKeys);
            c.countInvalid(check, issues, "statement");
            SupportStatus status = check.hasAnyValid() ? SupportStatus.SUPPORTED : SupportStatus.UNSUPPORTED;
            if (status == SupportStatus.SUPPORTED) {
                c.supported++;
            } else {
                c.unsupported++;
            }
            result.add(new ValidatedReasoning.Statement(s.statement().strip(), check.validFactKeys(),
                    check.validEvidenceKeys(), status));
        }
        return List.copyOf(result);
    }

    private String safeText(String text, Counters c, List<String> issues) {
        if (text == null || text.isBlank()) {
            return null;
        }
        List<SafetyGuardrail.Finding> findings = guardrail.inspect(text);
        if (findings.isEmpty()) {
            return text.strip();
        }
        c.withheld++;
        issues.add("Narrative text withheld by safety validator (" + findings.getFirst().type() + ")");
        return SafetyGuardrail.withheldPlaceholder(findings);
    }

    private List<String> safeTexts(List<String> texts, Counters c, List<String> issues) {
        List<String> result = new ArrayList<>();
        for (String text : texts) {
            if (guardrail.inspect(text).isEmpty()) {
                result.add(text.strip());
            } else {
                c.withheld++;
                issues.add("List item withheld by safety validator");
            }
        }
        return List.copyOf(result);
    }

    private List<SafetyGuardrail.Finding> findings(String... texts) {
        List<SafetyGuardrail.Finding> all = new ArrayList<>();
        for (String t : texts) {
            all.addAll(guardrail.inspect(t));
        }
        return all;
    }

    private static final class Counters {
        int supported;
        int unsupported;
        int withheld;
        int removedCodes;
        int capped;
        int invalidCitations;
        int dropped;

        void countInvalid(CitationCheck check, List<String> issues, String what) {
            if (check.hasInvalid()) {
                invalidCitations += check.invalid().size();
                issues.add("Invalid citation(s) removed from " + what + ": " + check.invalid().size());
            }
        }
    }
}
