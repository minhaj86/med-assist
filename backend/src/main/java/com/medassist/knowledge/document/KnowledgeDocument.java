package com.medassist.knowledge.document;

import com.medassist.common.domain.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A medical knowledge document (one version of a document series) with provenance and ingestion state. */
@Entity
@Table(name = "knowledge_documents")
@AttributeOverride(name = "version", column = @Column(name = "version_lock", nullable = false))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KnowledgeDocument extends AuditableEntity {

    @Column(name = "series_id", nullable = false, updatable = false)
    private UUID seriesId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "source_organization", nullable = false, length = 300)
    private String sourceOrganization;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "jurisdiction", length = 100)
    private String jurisdiction;

    /** The source document's own version label (optimistic locking uses column version_lock). */
    @Column(name = "version", nullable = false, updatable = false, length = 50)
    private String documentVersion;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "checksum", nullable = false, updatable = false, length = 64)
    private String checksum;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "original_filename", updatable = false, length = 300)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 30)
    private IngestionStatus ingestionStatus;

    @Column(name = "ingestion_error", length = 1000)
    private String ingestionError;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "embedding_model", length = 200)
    private String embeddingModel;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    /** Conditions this document discusses (curated by knowledge managers; display names, no invented codes). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_tags", nullable = false)
    private String conditionTags;

    @Column(name = "synthetic", nullable = false, updatable = false)
    private boolean synthetic;

    @Column(name = "supersedes_id", updatable = false)
    private UUID supersedesId;

    @Column(name = "superseded_by_id")
    private UUID supersededById;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "uploaded_by", nullable = false, updatable = false, length = 100)
    private String uploadedBy;

    public record Metadata(String title, DocumentType documentType, String sourceOrganization, String sourceUrl,
                           String jurisdiction, String version, LocalDate publicationDate, LocalDate reviewDate,
                           ApprovalStatus approvalStatus, String conditionTagsJson) {
    }

    public static KnowledgeDocument register(UUID seriesId, Metadata m, String checksum, String contentType,
                                             String originalFilename, long sizeBytes, boolean synthetic,
                                             UUID supersedesId, String uploadedBy, Instant uploadedAt) {
        KnowledgeDocument d = new KnowledgeDocument(UUID.randomUUID());
        d.seriesId = seriesId == null ? d.getId() : seriesId;
        d.title = Objects.requireNonNull(m.title());
        d.documentType = Objects.requireNonNull(m.documentType());
        d.sourceOrganization = Objects.requireNonNull(m.sourceOrganization());
        d.sourceUrl = m.sourceUrl();
        d.jurisdiction = m.jurisdiction();
        d.documentVersion = Objects.requireNonNull(m.version());
        d.publicationDate = m.publicationDate();
        d.reviewDate = m.reviewDate();
        d.approvalStatus = Objects.requireNonNull(m.approvalStatus());
        d.conditionTags = m.conditionTagsJson();
        d.enabled = true;
        d.checksum = checksum;
        d.contentType = contentType;
        d.originalFilename = originalFilename;
        d.sizeBytes = sizeBytes;
        d.synthetic = synthetic;
        d.supersedesId = supersedesId;
        d.ingestionStatus = IngestionStatus.PENDING;
        d.uploadedBy = uploadedBy;
        d.uploadedAt = uploadedAt;
        return d;
    }

    private KnowledgeDocument(UUID id) {
        super(id);
    }

    public void ingestionStage(IngestionStatus status) {
        this.ingestionStatus = status;
        this.ingestionError = null;
    }

    public void indexed(int chunkCount, Integer pageCount, String extractedText, String embeddingModel, Instant at) {
        this.ingestionStatus = IngestionStatus.INDEXED;
        this.chunkCount = chunkCount;
        this.pageCount = pageCount;
        this.extractedText = extractedText;
        this.embeddingModel = embeddingModel;
        this.lastIndexedAt = at;
        this.ingestionError = null;
    }

    public void failed(String message) {
        this.ingestionStatus = IngestionStatus.FAILED;
        this.ingestionError = message;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void changeApproval(ApprovalStatus status) {
        this.approvalStatus = Objects.requireNonNull(status);
    }

    public void changeConditionTags(String json) {
        this.conditionTags = json;
    }

    public void supersededBy(UUID newerVersionId) {
        this.supersededById = newerVersionId;
        this.enabled = false;
    }

    public boolean isUsableForRetrieval() {
        return enabled && approvalStatus == ApprovalStatus.APPROVED && ingestionStatus == IngestionStatus.INDEXED;
    }
}
