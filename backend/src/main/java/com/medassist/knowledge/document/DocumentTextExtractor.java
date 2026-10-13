package com.medassist.knowledge.document;

/** Port for format-specific text extraction. */
public interface DocumentTextExtractor {

    boolean supports(DocumentFormat format);

    ExtractedDocument extract(byte[] content);
}
