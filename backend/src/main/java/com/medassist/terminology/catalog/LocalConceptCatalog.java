package com.medassist.terminology.catalog;

import com.medassist.common.json.JsonCodec;
import com.medassist.terminology.domain.ConceptDomain;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/** Curated, reviewable concept catalog loaded from {@code terminology/local-concept-catalog.json}. */
@Component
public class LocalConceptCatalog {

    private final List<CatalogEntry> entries;
    private final Map<ConceptDomain, Map<String, CatalogEntry>> bySynonym = new HashMap<>();

    @Autowired
    public LocalConceptCatalog(JsonCodec jsonCodec) {
        this(load(jsonCodec));
    }

    LocalConceptCatalog(List<CatalogEntry> entries) {
        this.entries = List.copyOf(entries);
        for (CatalogEntry entry : this.entries) {
            Map<String, CatalogEntry> index = bySynonym.computeIfAbsent(entry.domain(), d -> new HashMap<>());
            index.put(key(entry.displayName()), entry);
            entry.synonyms().forEach(s -> index.put(key(s), entry));
        }
    }

    public Optional<CatalogEntry> find(String text, ConceptDomain domain) {
        if (text == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySynonym.getOrDefault(domain, Map.of()).get(key(text)));
    }

    public Optional<CatalogEntry> findByCode(String code) {
        return entries.stream().filter(e -> code != null && code.equals(e.code())).findFirst();
    }

    public List<CatalogEntry> search(String query, ConceptDomain domain, int limit) {
        String q = key(query == null ? "" : query);
        List<CatalogEntry> matches = new ArrayList<>();
        for (CatalogEntry entry : entries) {
            if (domain != null && entry.domain() != domain) {
                continue;
            }
            boolean match = q.isEmpty() || key(entry.displayName()).contains(q)
                    || entry.synonyms().stream().anyMatch(s -> key(s).contains(q));
            if (match && !matches.contains(entry)) {
                matches.add(entry);
            }
            if (matches.size() >= limit) {
                break;
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public int size() {
        return entries.size();
    }

    static String key(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static List<CatalogEntry> load(JsonCodec jsonCodec) {
        try (InputStream in = new ClassPathResource("terminology/local-concept-catalog.json").getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return jsonCodec.read(json, new TypeReference<List<CatalogEntry>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load local concept catalog", e);
        }
    }
}
