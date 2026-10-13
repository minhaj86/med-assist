package com.medassist.knowledge.citation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Verifies that citations reference evidence actually provided to the model: clinical fact keys (F1..Fn from the
 * encounter) and knowledge evidence keys (K1..Kn from retrieval). Anything else - invented ids, titles, URLs - is
 * invalid by construction.
 */
@Component
public class CitationValidator {

    private static final Pattern KEY = Pattern.compile("^[FK][1-9][0-9]{0,3}$");

    public record CitationCheck(List<String> validFactKeys, List<String> validEvidenceKeys, List<String> invalid) {

        public boolean hasAnyValid() {
            return !validFactKeys.isEmpty() || !validEvidenceKeys.isEmpty();
        }

        public boolean hasInvalid() {
            return !invalid.isEmpty();
        }
    }

    public CitationCheck check(Collection<String> cited, Set<String> knownFactKeys, Set<String> knownEvidenceKeys) {
        Set<String> facts = new LinkedHashSet<>();
        Set<String> evidence = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        if (cited != null) {
            for (String raw : cited) {
                String key = raw == null ? "" : raw.trim().toUpperCase();
                if (!KEY.matcher(key).matches()) {
                    invalid.add(raw);
                } else if (key.startsWith("F") && knownFactKeys.contains(key)) {
                    facts.add(key);
                } else if (key.startsWith("K") && knownEvidenceKeys.contains(key)) {
                    evidence.add(key);
                } else {
                    invalid.add(raw);
                }
            }
        }
        return new CitationCheck(List.copyOf(facts), List.copyOf(evidence), List.copyOf(invalid));
    }
}
