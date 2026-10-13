package com.medassist.diagnosis.context;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Removes direct identifiers from free text before it is used in decision-support context (and therefore before
 * it can reach an external AI provider): the patient's names and external identifier, e-mail addresses and
 * phone-number-like sequences.
 */
public final class FreeTextPseudonymizer {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)\\+?\\d[\\d ()-]{7,}\\d(?!\\w)");

    private final List<String> identifiers;

    public FreeTextPseudonymizer(List<String> identifiers) {
        this.identifiers = identifiers.stream()
                .filter(s -> s != null && s.trim().length() >= 2)
                .map(String::trim)
                .toList();
    }

    public String scrub(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = EMAIL.matcher(text).replaceAll("[redacted-email]");
        result = PHONE.matcher(result).replaceAll("[redacted-number]");
        for (String identifier : identifiers) {
            result = Pattern.compile("(?i)\\b" + Pattern.quote(identifier.toLowerCase(Locale.ROOT)) + "\\b")
                    .matcher(result).replaceAll("[patient]");
        }
        return result;
    }
}
