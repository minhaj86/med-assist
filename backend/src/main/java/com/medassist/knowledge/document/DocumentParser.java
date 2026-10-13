package com.medassist.knowledge.document;

import java.util.List;
import org.springframework.stereotype.Component;

/** Selects the extractor for a format. */
@Component
public class DocumentParser {

    private final List<DocumentTextExtractor> extractors;

    public DocumentParser(List<DocumentTextExtractor> extractors) {
        this.extractors = extractors;
    }

    public ExtractedDocument parse(DocumentFormat format, byte[] content) {
        return extractors.stream().filter(e -> e.supports(format)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No extractor for " + format))
                .extract(content);
    }
}
