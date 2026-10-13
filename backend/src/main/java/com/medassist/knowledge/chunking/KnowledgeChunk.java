package com.medassist.knowledge.chunking;

import com.medassist.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A retrievable passage of a knowledge document with its provenance metadata. */
@Entity
@Table(name = "knowledge_chunks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeChunk extends BaseEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;

    @Column(name = "section", length = 300, updatable = false)
    private String section;

    @Column(name = "page_number", updatable = false)
    private Integer pageNumber;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "char_count", nullable = false, updatable = false)
    private int charCount;

    @Column(name = "token_estimate", nullable = false, updatable = false)
    private int tokenEstimate;

    /** documentId, title, section, page, source, documentVersion, publicationDate, chunkIndex, ... */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, updatable = false)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public KnowledgeChunk(UUID documentId, TextChunk chunk, String metadataJson, Instant createdAt) {
        super(UUID.randomUUID());
        this.documentId = documentId;
        this.chunkIndex = chunk.index();
        this.section = chunk.section() == null ? null
                : chunk.section().length() > 300 ? chunk.section().substring(0, 300) : chunk.section();
        this.pageNumber = chunk.pageNumber();
        this.content = chunk.content();
        this.charCount = chunk.content().length();
        this.tokenEstimate = chunk.tokenEstimate();
        this.metadata = metadataJson;
        this.createdAt = createdAt;
    }
}
