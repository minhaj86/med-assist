package com.medassist.knowledge.document;

public enum IngestionStatus {
    PENDING,
    EXTRACTING,
    CHUNKING,
    EMBEDDING,
    INDEXED,
    FAILED
}
