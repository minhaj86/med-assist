package com.medassist.ai.reasoning;

import com.medassist.clinicalrules.domain.RuleResult;
import com.medassist.clinicalrules.domain.SupportingObservation;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalFact;
import com.medassist.diagnosis.differential.DifferentialCandidate;
import com.medassist.diagnosis.reasoning.ReasoningInput;
import com.medassist.diagnosis.scoring.ScoreResult;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats pseudonymized context, deterministic results and retrieved knowledge into prompt variables. Contains no
 * prompt wording - templates live in the prompt catalog.
 */
public final class ReasoningPromptVariables {

    static final int MAX_EVIDENCE_CHARS = 1500;
    static final int MAX_DIFFERENTIALS = 6;

    private ReasoningPromptVariables() {
    }

    public static Map<String, String> forAssessment(ReasoningInput input) {
        Map<String, String> v = new LinkedHashMap<>(contextVariables(input.context()));
        v.put("ruleAlerts", ruleAlerts(input.rules().triggered()));
        v.put("scores", scores(input.scores()));
        v.put("knowledge", knowledge(input.evidence()));
        v.put("candidates", candidates(input.candidates()));
        v.put("maxDifferentials", String.valueOf(MAX_DIFFERENTIALS));
        return v;
    }

    public static Map<String, String> contextVariables(ClinicalContext context) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("demographics", context.ageYears() + " years, biological sex "
                + context.biologicalSex().name().toLowerCase(Locale.ROOT));
        v.put("encounterType", context.encounterType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        v.put("facts", facts(context.facts()));
        return v;
    }

    static String facts(List<ClinicalFact> facts) {
        if (facts.isEmpty()) {
            return "(no clinical facts recorded)";
        }
        return facts.stream().map(f -> f.key() + ": " + sanitize(f.description())).collect(Collectors.joining("\n"));
    }

    static String ruleAlerts(List<RuleResult> triggered) {
        if (triggered.isEmpty()) {
            return "(none)";
        }
        return triggered.stream().map(r -> "- [" + r.severity() + "] " + r.ruleName() + ": " + r.explanation()
                        + (r.supportingObservations().isEmpty() ? "" : " (facts: " + r.supportingObservations().stream()
                        .map(SupportingObservation::factKey).collect(Collectors.joining(", ")) + ")"))
                .collect(Collectors.joining("\n"));
    }

    static String scores(List<ScoreResult> scores) {
        if (scores.isEmpty()) {
            return "(none)";
        }
        return scores.stream().map(s -> "- " + s.name() + " v" + s.version() + ": "
                        + (s.score() == null ? "not calculated" : s.score() + "/" + s.maxScore()) + ". " + s.interpretation())
                .collect(Collectors.joining("\n"));
    }

    public static String knowledge(List<RetrievedClinicalEvidence> evidence) {
        if (evidence.isEmpty()) {
            return "(no approved knowledge passages were retrieved)";
        }
        return evidence.stream().map(e -> e.evidenceKey() + " [source: " + sanitize(e.documentTitle())
                        + "; version " + e.documentVersion()
                        + (e.section() == null ? "" : "; section " + sanitize(e.section()))
                        + (e.page() == null ? "" : "; page " + e.page()) + "]:\n" + sanitize(truncate(e.text())))
                .collect(Collectors.joining("\n\n"));
    }

    static String candidates(List<DifferentialCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "(none)";
        }
        return candidates.stream().map(c -> "- " + sanitize(c.conditionName()) + " (discussed in "
                + String.join(", ", c.evidenceKeys()) + ")").collect(Collectors.joining("\n"));
    }

    /** Prevents data from closing the prompt's data blocks (basic prompt-injection hygiene). */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("(?i)</?\\s*(facts|knowledge|assessment|conversation)\\s*>", "")
                .replace("{{", "{ {").replace("}}", "} }");
    }

    private static String truncate(String text) {
        return text.length() <= MAX_EVIDENCE_CHARS ? text : text.substring(0, MAX_EVIDENCE_CHARS) + " [...]";
    }
}
