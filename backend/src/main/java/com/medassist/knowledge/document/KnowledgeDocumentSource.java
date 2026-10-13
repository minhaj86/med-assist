package com.medassist.knowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Original uploaded bytes, kept so documents can be re-extracted and re-indexed. */
@Entity
@Table(name = "knowledge_document_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDocumentSource {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "content", nullable = false)
    private byte[] content;

    public KnowledgeDocumentSource(UUID documentId, byte[] content) {
        this.documentId = documentId;
        this.content = content;
    }
}
