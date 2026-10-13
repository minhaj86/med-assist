package com.medassist.ai.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic language guardrails applied to every AI-generated sentence before display. Flags definitive
 * diagnostic language, fabricated-looking references (URLs, DOIs, author citations, named guideline bodies) and
 * numeric probabilities.
 */
@Component
public class SafetyGuardrail {

    public enum FindingType {
        DEFINITIVE_DIAGNOSIS_LANGUAGE,
        FABRICATED_REFERENCE,
        NUMERIC_PROBABILITY
    }

    public record Finding(FindingType type, String matched) {
    }

    private record Rule(FindingType type, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(FindingType.DEFINITIVE_DIAGNOSIS_LANGUAGE, Pattern.compile(
                    "(?i)\\b(definitely|certainly|undoubtedly|unequivocally|without (a )?doubt|beyond doubt)\\b")),
            new Rule(FindingType.DEFINITIVE_DIAGNOSIS_LANGUAGE, Pattern.compile(
                    "(?i)\\b(confirmed diagnosis|diagnosis (is|was) confirmed|is diagnosed with|has been diagnosed with|"
                            + "the diagnosis is)\\b")),
            new Rule(FindingType.DEFINITIVE_DIAGNOSIS_LANGUAGE, Pattern.compile(
                    "(?i)\\b(has been|is|was|were|are|can be) (ruled out|excluded)\\b|\\brules out\\b")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile("(?i)\\b(https?://|www\\.)\\S+")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile("(?i)\\bdoi\\s*:|\\b10\\.\\d{4,9}/\\S+|\\bpmid\\b")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile("(?i)\\bet al\\.?")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile(
                    "\\b[A-Z][A-Za-z]+,? \\(?(19|20)\\d{2}\\)|\\((19|20)\\d{2}\\)")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile(
                    "\\b(according to|as recommended by|recommended by) (the )?[A-Z]{2,}[A-Za-z/&-]*\\b")),
            new Rule(FindingType.FABRICATED_REFERENCE, Pattern.compile(
                    "\\b[A-Z]{2,}[A-Za-z/&-]* (guideline|guidelines|guidance|recommendations?)\\b")),
            new Rule(FindingType.NUMERIC_PROBABILITY, Pattern.compile(
                    "(?i)\\b\\d{1,3}(\\.\\d+)?\\s?%\\s*(probability|chance|likelihood|likely|certain|risk of having)")),
            new Rule(FindingType.NUMERIC_PROBABILITY, Pattern.compile("(?i)\\bprobability of \\d")));

    public List<Finding> inspect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            if (m.find()) {
                findings.add(new Finding(rule.type(), m.group()));
            }
        }
        return List.copyOf(findings);
    }

    public static String withheldPlaceholder(List<Finding> findings) {
        String reasons = findings.stream().map(f -> switch (f.type()) {
            case DEFINITIVE_DIAGNOSIS_LANGUAGE -> "definitive diagnostic language";
            case FABRICATED_REFERENCE -> "an unverifiable reference";
            case NUMERIC_PROBABILITY -> "a numeric probability";
        }).distinct().reduce((a, b) -> a + " and " + b).orElse("disallowed content");
        return "[Withheld by the safety validator: the AI text contained " + reasons + ".]";
    }
}
