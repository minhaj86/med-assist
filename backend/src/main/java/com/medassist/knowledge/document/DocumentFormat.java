package com.medassist.knowledge.document;

import com.medassist.common.error.DomainRuleViolationException;
import java.util.Locale;

public enum DocumentFormat {
    PDF("application/pdf"),
    PLAIN_TEXT("text/plain"),
    MARKDOWN("text/markdown");

    private final String mediaType;

    DocumentFormat(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    /** Determines the format from the file name (authoritative) and declared content type. */
    public static DocumentFormat detect(String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") || type.equals("application/pdf")) {
            return PDF;
        }
        if (name.endsWith(".md") || name.endsWith(".markdown") || type.equals("text/markdown")) {
            return MARKDOWN;
        }
        if (name.endsWith(".txt") || type.equals("text/plain")) {
            return PLAIN_TEXT;
        }
        throw new DomainRuleViolationException("Unsupported document type: only PDF, TXT and Markdown are accepted");
    }

    public static DocumentFormat fromMediaType(String mediaType) {
        for (DocumentFormat f : values()) {
            if (f.mediaType.equals(mediaType)) {
                return f;
            }
        }
        throw new IllegalArgumentException("Unknown media type " + mediaType);
    }
}
