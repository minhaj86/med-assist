package com.medassist.knowledge.document;

import java.util.List;
import java.util.stream.Collectors;

/** Raw text extracted from a document, page by page when the format has pages. */
public record ExtractedDocument(List<Page> pages) {

    public record Page(Integer pageNumber, String text) {
    }

    public ExtractedDocument {
        pages = List.copyOf(pages);
    }

    public String fullText() {
        return pages.stream().map(Page::text).collect(Collectors.joining("\n\n"));
    }

    public Integer pageCount() {
        return pages.stream().anyMatch(p -> p.pageNumber() != null) ? pages.size() : null;
    }
}
