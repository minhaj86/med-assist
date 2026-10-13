package com.medassist.terminology.catalog;

import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptDomain;
import java.util.List;

/** A curated catalog entry. {@code code} must only be present together with a documented {@code source}. */
public record CatalogEntry(ConceptDomain domain, String displayName, List<String> synonyms, CodingSystem system,
                           String code, String source) {

    public CatalogEntry {
        synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
        if (code != null && (system == null || source == null || source.isBlank())) {
            throw new IllegalArgumentException("Catalog code for '" + displayName + "' lacks a system or documented source");
        }
    }
}
